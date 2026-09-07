package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	goredis "github.com/redis/go-redis/v9"
)

const progressKeyFormat = "candle:1m:progress:%d"

type progressValue struct {
	AssetID                string `json:"asset_id"`
	MarketCode             string `json:"market_code"`
	IntervalMinutes        int16  `json:"interval_minutes"`
	OpenTimeUnixMilli      int64  `json:"open_time_unix_milli"`
	OpenPrice              string `json:"open_price"`
	HighPrice              string `json:"high_price"`
	LowPrice               string `json:"low_price"`
	ClosePrice             string `json:"close_price"`
	TradeVolume            string `json:"trade_volume"`
	TradeAmount            string `json:"trade_amount"`
	LastEventTimeUnixMilli int64  `json:"last_event_time_unix_milli"`
	LastSequenceID         int64  `json:"last_sequence_id"`
	SourceOffset           int64  `json:"source_offset"`
}

// ProgressRepository persists the live candle and its replay watermark as one
// Redis hash value per market.
type ProgressRepository struct {
	client *goredis.Client
}

func NewProgressRepository(client *goredis.Client) *ProgressRepository {
	return &ProgressRepository{client: client}
}

func (r *ProgressRepository) SaveBatch(
	ctx context.Context,
	partition int32,
	checkpoints []candle.Checkpoint,
) error {
	if len(checkpoints) == 0 {
		return nil
	}

	values := make(map[string]any, len(checkpoints))
	for _, checkpoint := range checkpoints {
		value, err := json.Marshal(toProgressValue(checkpoint))
		if err != nil {
			return exception.Wrap(exception.CodeRedisWriteFailed, err)
		}
		values[checkpoint.MarketCode] = value
	}

	if err := r.client.HSet(ctx, ProgressKey(partition), values).Err(); err != nil {
		return exception.Wrap(exception.CodeRedisWriteFailed, err)
	}

	return nil
}

// DeleteBatch removes finalized markets from one partition's progress hash.
func (r *ProgressRepository) DeleteBatch(
	ctx context.Context,
	partition int32,
	marketCodes []string,
) error {
	if len(marketCodes) == 0 {
		return nil
	}

	if err := r.client.HDel(ctx, ProgressKey(partition), marketCodes...).Err(); err != nil {
		return exception.Wrap(exception.CodeRedisWriteFailed, err)
	}

	return nil
}

func (r *ProgressRepository) Load(ctx context.Context, partition int32) ([]candle.Checkpoint, error) {
	values, err := r.client.HGetAll(ctx, ProgressKey(partition)).Result()
	if err != nil {
		return nil, exception.Wrap(exception.CodeRedisReadFailed, err)
	}

	checkpoints := make([]candle.Checkpoint, 0, len(values))
	for marketCode, encoded := range values {
		checkpoint, err := decodeCheckpoint(marketCode, []byte(encoded))
		if err != nil {
			return nil, err
		}
		checkpoints = append(checkpoints, checkpoint)
	}

	return checkpoints, nil
}

// ProgressKey returns the Redis hash key that owns live candle checkpoints for
// one Kafka partition.
func ProgressKey(partition int32) string {
	return fmt.Sprintf(progressKeyFormat, partition)
}

func toProgressValue(checkpoint candle.Checkpoint) progressValue {
	inProgress := checkpoint.InProgress
	return progressValue{
		AssetID:                inProgress.AssetID,
		MarketCode:             checkpoint.MarketCode,
		IntervalMinutes:        inProgress.IntervalMinutes,
		OpenTimeUnixMilli:      inProgress.OpenTime.UnixMilli(),
		OpenPrice:              inProgress.OpenPrice.String(),
		HighPrice:              inProgress.HighPrice.String(),
		LowPrice:               inProgress.LowPrice.String(),
		ClosePrice:             inProgress.ClosePrice.String(),
		TradeVolume:            inProgress.TradeVolume.String(),
		TradeAmount:            inProgress.TradeAmount.String(),
		LastEventTimeUnixMilli: inProgress.LastEventTime.UnixMilli(),
		LastSequenceID:         checkpoint.LastSequenceID,
		SourceOffset:           checkpoint.SourceOffset,
	}
}

func decodeCheckpoint(marketCode string, encoded []byte) (candle.Checkpoint, error) {
	var value progressValue
	if err := json.Unmarshal(encoded, &value); err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}
	if value.MarketCode == "" || value.MarketCode != marketCode || value.AssetID == "" ||
		value.IntervalMinutes != 1 || value.OpenTimeUnixMilli <= 0 ||
		value.LastEventTimeUnixMilli <= 0 || value.LastSequenceID <= 0 {
		return candle.Checkpoint{}, exception.New(exception.CodeRedisInvalidValue)
	}

	openPrice, err := candledomain.ParseAmount(value.OpenPrice)
	if err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}
	highPrice, err := candledomain.ParseAmount(value.HighPrice)
	if err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}
	lowPrice, err := candledomain.ParseAmount(value.LowPrice)
	if err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}
	closePrice, err := candledomain.ParseAmount(value.ClosePrice)
	if err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}
	tradeVolume, err := candledomain.ParseAmount(value.TradeVolume)
	if err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}
	tradeAmount, err := candledomain.ParseAmount(value.TradeAmount)
	if err != nil {
		return candle.Checkpoint{}, exception.Wrap(exception.CodeRedisInvalidValue, err)
	}

	return candle.Checkpoint{
		MarketCode:     value.MarketCode,
		LastSequenceID: value.LastSequenceID,
		SourceOffset:   value.SourceOffset,
		InProgress: candledomain.MinuteCandle{
			AssetID:         value.AssetID,
			IntervalMinutes: value.IntervalMinutes,
			OpenTime:        time.UnixMilli(value.OpenTimeUnixMilli).UTC(),
			OpenPrice:       openPrice,
			HighPrice:       highPrice,
			LowPrice:        lowPrice,
			ClosePrice:      closePrice,
			TradeVolume:     tradeVolume,
			TradeAmount:     tradeAmount,
			LastEventTime:   time.UnixMilli(value.LastEventTimeUnixMilli).UTC(),
		},
	}, nil
}
