package producer_test

import (
	"context"
	"slices"
	"testing"
	"time"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

type recordingStreamer struct {
	subscriptions chan []assetdomain.MarketDataCollectionTarget
}

func (s *recordingStreamer) Run(
	ctx context.Context,
	updates <-chan []assetdomain.MarketDataCollectionTarget,
	trades chan<- tradedomain.MarketTrade,
) error {
	defer close(trades)
	for {
		select {
		case targets := <-updates:
			s.subscriptions <- append([]assetdomain.MarketDataCollectionTarget(nil), targets...)
		case <-ctx.Done():
			return nil
		}
	}
}

func TestServiceUpdatesSubscriptionWhenOwnedShardsChange(t *testing.T) {
	bitcoin := assetdomain.MarketDataCollectionTarget{AssetID: "asset-1", MarketCode: "KRW-BTC"}
	ethereum := assetdomain.MarketDataCollectionTarget{AssetID: "asset-2", MarketCode: "KRW-ETH"}
	ripple := assetdomain.MarketDataCollectionTarget{AssetID: "asset-3", MarketCode: "KRW-XRP"}
	loader := &fakeTargetLoader{results: []targetLoadResult{{
		targets: []assetdomain.MarketDataCollectionTarget{bitcoin, ethereum, ripple},
	}}}
	streamer := &recordingStreamer{subscriptions: make(chan []assetdomain.MarketDataCollectionTarget, 3)}
	service := producer.NewService(producer.NewTargetWatcher(loader), streamer, time.Hour)
	shardChanges := make(chan []int32, 3)

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	serviceErrors := runService(ctx, service, shardChanges)

	shardChanges <- []int32{1, 4}
	assertSubscription(t, ctx, streamer.subscriptions, serviceErrors, []assetdomain.MarketDataCollectionTarget{ethereum, ripple})
	shardChanges <- []int32{3}
	assertSubscription(t, ctx, streamer.subscriptions, serviceErrors, []assetdomain.MarketDataCollectionTarget{bitcoin})
	shardChanges <- []int32{}
	assertSubscription(t, ctx, streamer.subscriptions, serviceErrors, []assetdomain.MarketDataCollectionTarget{})

	cancel()
	assertServiceStopped(t, serviceErrors)
}

func TestServiceUpdatesSubscriptionWhenEnabledTargetsChange(t *testing.T) {
	bitcoin := assetdomain.MarketDataCollectionTarget{AssetID: "asset-1", MarketCode: "KRW-BTC"}
	ethereum := assetdomain.MarketDataCollectionTarget{AssetID: "asset-2", MarketCode: "KRW-ETH"}
	loader := &fakeTargetLoader{results: []targetLoadResult{
		{targets: []assetdomain.MarketDataCollectionTarget{bitcoin}},
		{targets: []assetdomain.MarketDataCollectionTarget{bitcoin, ethereum}},
	}}
	streamer := &recordingStreamer{subscriptions: make(chan []assetdomain.MarketDataCollectionTarget, 2)}
	service := producer.NewService(producer.NewTargetWatcher(loader), streamer, 200*time.Millisecond)
	shardChanges := make(chan []int32, 1)
	shardChanges <- []int32{1, 3}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	serviceErrors := runService(ctx, service, shardChanges)

	assertSubscription(t, ctx, streamer.subscriptions, serviceErrors, []assetdomain.MarketDataCollectionTarget{bitcoin})
	assertSubscription(t, ctx, streamer.subscriptions, serviceErrors, []assetdomain.MarketDataCollectionTarget{bitcoin, ethereum})

	cancel()
	assertServiceStopped(t, serviceErrors)
}

func runService(
	ctx context.Context,
	service *producer.Service,
	shardChanges <-chan []int32,
) <-chan error {
	errors := make(chan error, 1)
	trades := make(chan tradedomain.MarketTrade)
	go func() {
		errors <- service.Run(ctx, shardChanges, trades)
	}()
	return errors
}

func assertSubscription(
	t *testing.T,
	ctx context.Context,
	subscriptions <-chan []assetdomain.MarketDataCollectionTarget,
	serviceErrors <-chan error,
	want []assetdomain.MarketDataCollectionTarget,
) {
	t.Helper()
	select {
	case got := <-subscriptions:
		if !slices.Equal(got, want) {
			t.Fatalf("subscription = %#v, want %#v", got, want)
		}
	case err := <-serviceErrors:
		t.Fatalf("Service.Run() stopped early: %v", err)
	case <-ctx.Done():
		t.Fatalf("subscription was not updated: %v", ctx.Err())
	}
}

func assertServiceStopped(t *testing.T, serviceErrors <-chan error) {
	t.Helper()
	select {
	case err := <-serviceErrors:
		if err != nil {
			t.Fatalf("Service.Run() error = %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("Service.Run() did not stop")
	}
}
