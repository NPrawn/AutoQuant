package producer

import (
	"context"
	"log/slog"

	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

const realtimeTradeQueueCapacity = 2048

type RealtimeTradePublisher interface {
	Publish(context.Context, tradedomain.MarketTrade) error
}

// RealtimeTradeDispatcher isolates Redis latency behind a bounded queue.
type RealtimeTradeDispatcher struct {
	publisher RealtimeTradePublisher
	trades    chan tradedomain.MarketTrade
}

func NewRealtimeTradeDispatcher(publisher RealtimeTradePublisher) *RealtimeTradeDispatcher {
	return &RealtimeTradeDispatcher{
		publisher: publisher,
		trades:    make(chan tradedomain.MarketTrade, realtimeTradeQueueCapacity),
	}
}

// TryPublish enqueues a trade without blocking. When the queue is full, it
// removes the oldest update so the live channel converges on recent data.
func (d *RealtimeTradeDispatcher) TryPublish(trade tradedomain.MarketTrade) bool {
	select {
	case d.trades <- trade:
		return true
	default:
	}

	select {
	case <-d.trades:
	default:
	}
	d.trades <- trade
	return false
}

func (d *RealtimeTradeDispatcher) Close() {
	close(d.trades)
}

func (d *RealtimeTradeDispatcher) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case trade, open := <-d.trades:
			if !open {
				return
			}
			if err := d.publisher.Publish(ctx, trade); err != nil {
				slog.Warn(
					"failed to publish realtime trade update",
					"error", err,
					"market_code", trade.MarketCode,
					"sequence_id", trade.SequenceID,
				)
			}
		}
	}
}
