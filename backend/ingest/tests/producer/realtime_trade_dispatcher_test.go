package producer_test

import (
	"context"
	"errors"
	"testing"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

type recordingRealtimePublisher struct {
	trades []tradedomain.MarketTrade
	err    error
}

func (p *recordingRealtimePublisher) Publish(
	_ context.Context,
	trade tradedomain.MarketTrade,
) error {
	p.trades = append(p.trades, trade)
	return p.err
}

func TestRealtimeTradeDispatcherPublishesQueuedTradesInOrder(t *testing.T) {
	publisher := &recordingRealtimePublisher{}
	dispatcher := producer.NewRealtimeTradeDispatcher(publisher)
	dispatcher.TryPublish(tradedomain.MarketTrade{MarketCode: "KRW-BTC", SequenceID: 1})
	dispatcher.TryPublish(tradedomain.MarketTrade{MarketCode: "KRW-BTC", SequenceID: 2})
	dispatcher.Close()

	dispatcher.Run(context.Background())

	if len(publisher.trades) != 2 || publisher.trades[0].SequenceID != 1 ||
		publisher.trades[1].SequenceID != 2 {
		t.Fatalf("published trades = %#v, want sequences 1 and 2", publisher.trades)
	}
}

func TestRealtimeTradeDispatcherContinuesAfterPublishError(t *testing.T) {
	publisher := &recordingRealtimePublisher{err: errors.New("redis unavailable")}
	dispatcher := producer.NewRealtimeTradeDispatcher(publisher)
	dispatcher.TryPublish(tradedomain.MarketTrade{MarketCode: "KRW-BTC", SequenceID: 1})
	dispatcher.TryPublish(tradedomain.MarketTrade{MarketCode: "KRW-ETH", SequenceID: 2})
	dispatcher.Close()

	dispatcher.Run(context.Background())

	if len(publisher.trades) != 2 {
		t.Fatalf("published trade count = %d, want 2", len(publisher.trades))
	}
}

func TestRealtimeTradeDispatcherDropsOldestTradeWhenQueueIsFull(t *testing.T) {
	publisher := &recordingRealtimePublisher{}
	dispatcher := producer.NewRealtimeTradeDispatcher(publisher)
	dropped := false
	lastSequenceID := int64(0)
	for sequenceID := int64(1); sequenceID <= 10000; sequenceID++ {
		lastSequenceID = sequenceID
		if !dispatcher.TryPublish(tradedomain.MarketTrade{SequenceID: sequenceID}) {
			dropped = true
			break
		}
	}
	if !dropped {
		t.Fatal("queue did not report a dropped realtime trade")
	}
	dispatcher.Close()

	dispatcher.Run(context.Background())

	if len(publisher.trades) == 0 || publisher.trades[0].SequenceID == 1 ||
		publisher.trades[len(publisher.trades)-1].SequenceID != lastSequenceID {
		t.Fatalf("published trades do not retain the newest queued update")
	}
}
