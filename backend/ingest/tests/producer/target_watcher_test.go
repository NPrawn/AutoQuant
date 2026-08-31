package producer_test

import (
	"context"
	"errors"
	"reflect"
	"testing"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/producer"
)

type targetLoadResult struct {
	targets []assetdomain.MarketDataCollectionTarget
	err     error
}

type fakeTargetLoader struct {
	results []targetLoadResult
	index   int
}

func (f *fakeTargetLoader) LoadEnabledTargets(
	context.Context,
) ([]assetdomain.MarketDataCollectionTarget, error) {
	result := f.results[f.index]
	if f.index < len(f.results)-1 {
		f.index++
	}
	return result.targets, result.err
}

func TestTargetWatcherRefresh(t *testing.T) {
	bitcoin := assetdomain.MarketDataCollectionTarget{AssetID: "asset-1", MarketCode: "KRW-BTC"}
	ethereum := assetdomain.MarketDataCollectionTarget{AssetID: "asset-2", MarketCode: "KRW-ETH"}
	databaseError := errors.New("database unavailable")
	loader := &fakeTargetLoader{results: []targetLoadResult{
		{targets: []assetdomain.MarketDataCollectionTarget{bitcoin}},
		{targets: []assetdomain.MarketDataCollectionTarget{bitcoin}},
		{targets: []assetdomain.MarketDataCollectionTarget{bitcoin, ethereum}},
		{err: databaseError},
	}}
	watcher := producer.NewTargetWatcher(loader)

	tests := []struct {
		name        string
		wantTargets []assetdomain.MarketDataCollectionTarget
		wantChanged bool
		wantError   error
	}{
		{name: "최초 목록을 변경으로 반환한다", wantTargets: []assetdomain.MarketDataCollectionTarget{bitcoin}, wantChanged: true},
		{name: "같은 목록은 변경으로 반환하지 않는다"},
		{name: "추가된 종목 목록을 반환한다", wantTargets: []assetdomain.MarketDataCollectionTarget{bitcoin, ethereum}, wantChanged: true},
		{name: "조회 오류를 반환한다", wantError: databaseError},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			gotTargets, gotChanged, gotError := watcher.Refresh(context.Background())
			if !reflect.DeepEqual(gotTargets, test.wantTargets) {
				t.Fatalf("targets = %#v, want %#v", gotTargets, test.wantTargets)
			}
			if gotChanged != test.wantChanged {
				t.Fatalf("changed = %v, want %v", gotChanged, test.wantChanged)
			}
			if !errors.Is(gotError, test.wantError) {
				t.Fatalf("error = %v, want %v", gotError, test.wantError)
			}
		})
	}
}
