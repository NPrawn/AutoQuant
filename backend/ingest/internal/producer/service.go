package producer

import (
	"context"
	"time"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	tradedomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/trade/domain"
)

type TradeStreamer interface {
	Run(
		ctx context.Context,
		updates <-chan []assetdomain.MarketDataCollectionTarget,
		trades chan<- tradedomain.MarketTrade,
	) error
}

// Service keeps the Upbit subscription synchronized with enabled targets owned by this Collector.
type Service struct {
	watcher         *TargetWatcher
	streamer        TradeStreamer
	refreshInterval time.Duration
}

func NewService(watcher *TargetWatcher, streamer TradeStreamer, refreshInterval time.Duration) *Service {
	return &Service{
		watcher:         watcher,
		streamer:        streamer,
		refreshInterval: refreshInterval,
	}
}

func (s *Service) Run(
	ctx context.Context,
	shardChanges <-chan []int32,
	trades chan<- tradedomain.MarketTrade,
) error {
	targets, _, err := s.watcher.Refresh(ctx)
	if err != nil {
		close(trades)
		return err
	}

	runContext, cancel := context.WithCancel(ctx)
	defer cancel()

	updates := make(chan []assetdomain.MarketDataCollectionTarget, 1)
	streamErrors := make(chan error, 1)
	go func() {
		streamErrors <- s.streamer.Run(runContext, updates, trades)
	}()

	ticker := time.NewTicker(s.refreshInterval)
	defer ticker.Stop()
	var ownedShards []int32
	var subscribedTargets []assetdomain.MarketDataCollectionTarget
	updateSubscription := func() {
		nextTargets := TargetsForShards(targets, ownedShards)
		if equalTargets(subscribedTargets, nextTargets) {
			return
		}
		subscribedTargets = nextTargets
		updates <- nextTargets
	}

	for {
		select {
		case <-ctx.Done():
			cancel()
			return <-streamErrors
		case err := <-streamErrors:
			return err
		case shards, open := <-shardChanges:
			if !open {
				shardChanges = nil
				continue
			}
			ownedShards = append(ownedShards[:0], shards...)
			updateSubscription()
		case <-ticker.C:
			nextTargets, changed, err := s.watcher.Refresh(ctx)
			if err != nil {
				cancel()
				<-streamErrors
				return err
			}
			if changed {
				targets = nextTargets
				updateSubscription()
			}
		}
	}
}
