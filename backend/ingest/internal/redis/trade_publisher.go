package redis

import (
	"context"
	"encoding/json"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"

	goredis "github.com/redis/go-redis/v9"
)

type realtimeTradeUpdate struct {
	AssetID           string `json:"asset_id"`
	MarketCode        string `json:"market_code"`
	Price             string `json:"price"`
	Volume            string `json:"volume"`
	Side              string `json:"side"`
	TradedAtUnixMilli int64  `json:"traded_at_unix_milli"`
	SequenceID        int64  `json:"sequence_id"`
}

// RealtimeTradePublisher publishes normalized trades for best-effort live views.
type RealtimeTradePublisher struct {
	client *goredis.Client
}

func NewRealtimeTradePublisher(client *goredis.Client) *RealtimeTradePublisher {
	return &RealtimeTradePublisher{client: client}
}

func (p *RealtimeTradePublisher) Publish(
	ctx context.Context,
	trade tradedomain.MarketTrade,
) error {
	payload, err := json.Marshal(realtimeTradeUpdate{
		AssetID:           trade.AssetID,
		MarketCode:        trade.MarketCode,
		Price:             trade.Price,
		Volume:            trade.Volume,
		Side:              string(trade.Side),
		TradedAtUnixMilli: trade.TradedAt.UnixMilli(),
		SequenceID:        trade.SequenceID,
	})
	if err != nil {
		return exception.Wrap(exception.CodeRedisPublishFailed, err)
	}

	if err := p.client.Publish(ctx, RealtimeTradeChannel, payload).Err(); err != nil {
		return exception.Wrap(exception.CodeRedisPublishFailed, err)
	}

	return nil
}
