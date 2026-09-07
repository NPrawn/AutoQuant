package consumer

import (
	"context"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
)

// DiscardingCache drops in-progress candles instead of caching them.
//
// It stands in until the Redis key and value format is agreed with backend/api,
// which owns the read side of that contract. Losing an in-progress candle only
// delays live rendering: the next trade of the same minute writes it again, and
// the finalized candle still reaches PostgreSQL.
type DiscardingCache struct{}

func (DiscardingCache) Save(context.Context, candledomain.MinuteCandle) error {
	return nil
}
