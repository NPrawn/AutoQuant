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

// Service keeps the Upbit subscription synchronized with enabled database targets.
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

func (s *Service) Run(ctx context.Context, trades chan<- tradedomain.MarketTrade) error {
	initialTargets, _, err := s.watcher.Refresh(ctx)
	if err != nil {
		close(trades)
		return err
	}

	runContext, cancel := context.WithCancel(ctx)
	defer cancel()

	updates := make(chan []assetdomain.MarketDataCollectionTarget, 1)
	updates <- initialTargets
	streamErrors := make(chan error, 1)
	go func() {
		streamErrors <- s.streamer.Run(runContext, updates, trades)
	}()

	ticker := time.NewTicker(s.refreshInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			cancel()
			return <-streamErrors
		case err := <-streamErrors:
			return err
		case <-ticker.C:
			targets, changed, err := s.watcher.Refresh(ctx)
			if err != nil {
				cancel()
				<-streamErrors
				return err
			}
			if changed {
				updates <- targets
			}
		}
	}
}
