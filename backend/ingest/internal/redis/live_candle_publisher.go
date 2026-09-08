package redis

import (
	"context"
	"encoding/json"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	goredis "github.com/redis/go-redis/v9"
)

type liveMinuteCandleUpdate struct {
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
}

// LiveMinuteCandlePublisher publishes one latest checkpoint per changed market.
type LiveMinuteCandlePublisher struct {
	client *goredis.Client
}

func NewLiveMinuteCandlePublisher(client *goredis.Client) *LiveMinuteCandlePublisher {
	return &LiveMinuteCandlePublisher{client: client}
}

func (p *LiveMinuteCandlePublisher) PublishBatch(
	ctx context.Context,
	checkpoints []candle.Checkpoint,
) error {
	if len(checkpoints) == 0 {
		return nil
	}

	payloads := make([][]byte, 0, len(checkpoints))
	for _, checkpoint := range checkpoints {
		current := checkpoint.InProgress
		payload, err := json.Marshal(liveMinuteCandleUpdate{
			AssetID:                current.AssetID,
			MarketCode:             checkpoint.MarketCode,
			IntervalMinutes:        current.IntervalMinutes,
			OpenTimeUnixMilli:      current.OpenTime.UnixMilli(),
			OpenPrice:              current.OpenPrice.String(),
			HighPrice:              current.HighPrice.String(),
			LowPrice:               current.LowPrice.String(),
			ClosePrice:             current.ClosePrice.String(),
			TradeVolume:            current.TradeVolume.String(),
			TradeAmount:            current.TradeAmount.String(),
			LastEventTimeUnixMilli: current.LastEventTime.UnixMilli(),
			LastSequenceID:         checkpoint.LastSequenceID,
		})
		if err != nil {
			return exception.Wrap(exception.CodeRedisPublishFailed, err)
		}
		payloads = append(payloads, payload)
	}

	_, err := p.client.Pipelined(ctx, func(pipe goredis.Pipeliner) error {
		for index, payload := range payloads {
			pipe.Publish(ctx, LiveCandleChannel(checkpoints[index].InProgress.IntervalMinutes), payload)
		}
		return nil
	})
	if err != nil {
		return exception.Wrap(exception.CodeRedisPublishFailed, err)
	}

	return nil
}
