package producer

import (
	"context"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
)

type TargetLoader interface {
	LoadEnabledTargets(ctx context.Context) ([]assetdomain.MarketDataCollectionTarget, error)
}

// TargetWatcher reports only changed collection target snapshots.
type TargetWatcher struct {
	loader  TargetLoader
	current []assetdomain.MarketDataCollectionTarget
}

func NewTargetWatcher(loader TargetLoader) *TargetWatcher {
	return &TargetWatcher{loader: loader}
}

func (w *TargetWatcher) Refresh(
	ctx context.Context,
) ([]assetdomain.MarketDataCollectionTarget, bool, error) {
	targets, err := w.loader.LoadEnabledTargets(ctx)
	if err != nil {
		return nil, false, err
	}
	if equalTargets(w.current, targets) {
		return nil, false, nil
	}

	w.current = append(w.current[:0], targets...)
	return append([]assetdomain.MarketDataCollectionTarget(nil), targets...), true, nil
}

func equalTargets(left, right []assetdomain.MarketDataCollectionTarget) bool {
	if len(left) != len(right) {
		return false
	}
	for index := range left {
		if left[index] != right[index] {
			return false
		}
	}
	return true
}
