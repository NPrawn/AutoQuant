package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"sort"
	"strconv"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	goredis "github.com/redis/go-redis/v9"
)

const (
	recentMinuteCandlesKeyFormat = "candle:1m:recent:%s"
	recentMinuteCandleLimit      = int64(1000)
)

type recentMinuteCandleValue struct {
	AssetID                string `json:"asset_id"`
	IntervalMinutes        int16  `json:"interval_minutes"`
	OpenTimeUnixMilli      int64  `json:"open_time_unix_milli"`
	OpenPrice              string `json:"open_price"`
	HighPrice              string `json:"high_price"`
	LowPrice               string `json:"low_price"`
	ClosePrice             string `json:"close_price"`
	TradeVolume            string `json:"trade_volume"`
	TradeAmount            string `json:"trade_amount"`
	LastEventTimeUnixMilli int64  `json:"last_event_time_unix_milli"`
}

type recentMinuteCandleEntry struct {
	score   int64
	payload []byte
}

// RecentMinuteCandleRepository keeps the latest finalized candles per asset.
type RecentMinuteCandleRepository struct {
	client *goredis.Client
}

func NewRecentMinuteCandleRepository(client *goredis.Client) *RecentMinuteCandleRepository {
	return &RecentMinuteCandleRepository{client: client}
}

func (r *RecentMinuteCandleRepository) SaveBatch(
	ctx context.Context,
	candles []candledomain.MinuteCandle,
) error {
	if len(candles) == 0 {
		return nil
	}

	entriesByKey := make(map[string][]recentMinuteCandleEntry)
	for _, candle := range candles {
		payload, err := json.Marshal(toRecentMinuteCandleValue(candle))
		if err != nil {
			return exception.Wrap(exception.CodeRedisWriteFailed, err)
		}
		key := RecentMinuteCandlesKey(candle.AssetID)
		entriesByKey[key] = append(entriesByKey[key], recentMinuteCandleEntry{
			score:   candle.OpenTime.UnixMilli(),
			payload: payload,
		})
	}

	keys := make([]string, 0, len(entriesByKey))
	for key := range entriesByKey {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	_, err := r.client.Pipelined(ctx, func(pipe goredis.Pipeliner) error {
		for _, key := range keys {
			for _, entry := range entriesByKey[key] {
				score := strconv.FormatInt(entry.score, 10)
				pipe.ZRemRangeByScore(ctx, key, score, score)
				pipe.ZAdd(ctx, key, goredis.Z{
					Score:  float64(entry.score),
					Member: entry.payload,
				})
			}
			pipe.ZRemRangeByRank(ctx, key, 0, -(recentMinuteCandleLimit + 1))
		}
		return nil
	})
	if err != nil {
		return exception.Wrap(exception.CodeRedisWriteFailed, err)
	}

	return nil
}

// RecentMinuteCandlesKey returns the Sorted Set key for one asset's finalized candles.
func RecentMinuteCandlesKey(assetID string) string {
	return fmt.Sprintf(recentMinuteCandlesKeyFormat, assetID)
}

func toRecentMinuteCandleValue(candle candledomain.MinuteCandle) recentMinuteCandleValue {
	return recentMinuteCandleValue{
		AssetID:                candle.AssetID,
		IntervalMinutes:        candle.IntervalMinutes,
		OpenTimeUnixMilli:      candle.OpenTime.UnixMilli(),
		OpenPrice:              candle.OpenPrice.String(),
		HighPrice:              candle.HighPrice.String(),
		LowPrice:               candle.LowPrice.String(),
		ClosePrice:             candle.ClosePrice.String(),
		TradeVolume:            candle.TradeVolume.String(),
		TradeAmount:            candle.TradeAmount.String(),
		LastEventTimeUnixMilli: candle.LastEventTime.UnixMilli(),
	}
}
