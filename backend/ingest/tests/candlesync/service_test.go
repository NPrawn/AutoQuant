package candlesync_test

import (
	"context"
	"errors"
	"reflect"
	"testing"
	"time"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlesync"
)

type targetLoader struct {
	targets []assetdomain.MarketDataCollectionTarget
}

func (l *targetLoader) LoadEnabledTargets(context.Context) ([]assetdomain.MarketDataCollectionTarget, error) {
	return append([]assetdomain.MarketDataCollectionTarget(nil), l.targets...), nil
}

type sourceCall struct {
	market string
	to     time.Time
	count  int
}

type candleSource struct {
	calls   []sourceCall
	candles []candledomain.MinuteCandle
	err     error
}

func (s *candleSource) ListMinuteCandles(
	_ context.Context,
	market string,
	to time.Time,
	count int,
) ([]candledomain.MinuteCandle, error) {
	s.calls = append(s.calls, sourceCall{market: market, to: to, count: count})
	return append([]candledomain.MinuteCandle(nil), s.candles...), s.err
}

type initialization struct {
	assetID           string
	marketCode        string
	historyTargetFrom time.Time
	anchor            time.Time
}

type appliedWindow struct {
	direction candlesync.Direction
	from      time.Time
	to        time.Time
	candles   []candledomain.MinuteCandle
}

type syncRepository struct {
	state           *candlesync.State
	initializations []initialization
	windows         []appliedWindow
	applyErr        error
}

func (r *syncRepository) Initialize(
	_ context.Context,
	assetID string,
	marketCode string,
	historyTargetFrom time.Time,
	anchor time.Time,
) (candlesync.State, error) {
	r.initializations = append(r.initializations, initialization{
		assetID:           assetID,
		marketCode:        marketCode,
		historyTargetFrom: historyTargetFrom,
		anchor:            anchor,
	})
	if r.state != nil {
		state := *r.state
		return state, nil
	}
	return candlesync.State{
		AssetID:           assetID,
		MarketCode:        marketCode,
		IntervalMinutes:   1,
		HistoryTargetFrom: historyTargetFrom,
		VerifiedFrom:      anchor,
		VerifiedTo:        anchor,
	}, nil
}

func (r *syncRepository) ApplyWindow(
	_ context.Context,
	_ candlesync.State,
	direction candlesync.Direction,
	from time.Time,
	to time.Time,
	candles []candledomain.MinuteCandle,
) error {
	r.windows = append(r.windows, appliedWindow{
		direction: direction,
		from:      from,
		to:        to,
		candles:   append([]candledomain.MinuteCandle(nil), candles...),
	})
	return r.applyErr
}

type recentCache struct {
	batches [][]candledomain.MinuteCandle
	err     error
}

func (c *recentCache) SaveBatch(_ context.Context, candles []candledomain.MinuteCandle) error {
	c.batches = append(c.batches, append([]candledomain.MinuteCandle(nil), candles...))
	return c.err
}

func TestServiceExtendsHistoryFromEmptyAnchor(t *testing.T) {
	now := time.Date(2026, 9, 7, 12, 15, 30, 0, time.UTC)
	historyFrom := time.Date(2026, 9, 7, 5, 0, 0, 0, time.UTC)
	source := &candleSource{}
	repository := &syncRepository{}
	service := candlesync.NewService(
		&targetLoader{targets: []assetdomain.MarketDataCollectionTarget{{
			AssetID:    "asset-1",
			MarketCode: "KRW-BTC",
		}}},
		source,
		repository,
		&recentCache{},
		historyFrom,
		2,
	)

	if err := service.Run(context.Background(), now); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	anchor := time.Date(2026, 9, 7, 12, 14, 0, 0, time.UTC)
	if len(repository.initializations) != 1 || !repository.initializations[0].anchor.Equal(anchor) {
		t.Fatalf("initializations = %#v, want anchor %v", repository.initializations, anchor)
	}
	if len(repository.windows) != 2 {
		t.Fatalf("window count = %d, want 2", len(repository.windows))
	}
	wantFirstFrom := anchor.Add(-200 * time.Minute)
	if repository.windows[0].direction != candlesync.DirectionHistory ||
		!repository.windows[0].from.Equal(wantFirstFrom) ||
		!repository.windows[0].to.Equal(anchor) {
		t.Fatalf("first window = %#v, want [%v, %v)", repository.windows[0], wantFirstFrom, anchor)
	}
	if source.calls[0].count != 200 {
		t.Fatalf("request count = %d, want 200", source.calls[0].count)
	}
}

func TestServiceVerifiesLatestBeforeHistory(t *testing.T) {
	cutoff := time.Date(2026, 9, 7, 12, 14, 0, 0, time.UTC)
	historyFrom := cutoff.Add(-400 * time.Minute)
	repository := &syncRepository{state: &candlesync.State{
		AssetID:           "asset-1",
		MarketCode:        "KRW-BTC",
		IntervalMinutes:   1,
		HistoryTargetFrom: historyFrom,
		VerifiedFrom:      cutoff.Add(-200 * time.Minute),
		VerifiedTo:        cutoff.Add(-5 * time.Minute),
	}}
	service := candlesync.NewService(
		&targetLoader{targets: []assetdomain.MarketDataCollectionTarget{{AssetID: "asset-1", MarketCode: "KRW-BTC"}}},
		&candleSource{},
		repository,
		&recentCache{},
		historyFrom,
		1,
	)

	if err := service.Run(context.Background(), cutoff.Add(time.Minute)); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	wantDirections := []candlesync.Direction{candlesync.DirectionLatest, candlesync.DirectionHistory}
	gotDirections := make([]candlesync.Direction, 0, len(repository.windows))
	for _, window := range repository.windows {
		gotDirections = append(gotDirections, window.direction)
	}
	if !reflect.DeepEqual(gotDirections, wantDirections) {
		t.Fatalf("directions = %v, want %v", gotDirections, wantDirections)
	}
}

func TestServiceDoesNotFetchCompletedRange(t *testing.T) {
	cutoff := time.Date(2026, 9, 7, 12, 14, 0, 0, time.UTC)
	source := &candleSource{}
	repository := &syncRepository{state: &candlesync.State{
		AssetID:           "asset-1",
		MarketCode:        "KRW-BTC",
		IntervalMinutes:   1,
		HistoryTargetFrom: cutoff.Add(-time.Hour),
		VerifiedFrom:      cutoff.Add(-time.Hour),
		VerifiedTo:        cutoff,
	}}
	service := candlesync.NewService(
		&targetLoader{targets: []assetdomain.MarketDataCollectionTarget{{AssetID: "asset-1", MarketCode: "KRW-BTC"}}},
		source,
		repository,
		&recentCache{},
		cutoff.Add(-time.Hour),
		10,
	)

	if err := service.Run(context.Background(), cutoff.Add(time.Minute)); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(source.calls) != 0 {
		t.Fatalf("source calls = %#v, want none", source.calls)
	}
}

func TestServiceUsesTradingStartAsHistoryBoundary(t *testing.T) {
	now := time.Date(2026, 9, 7, 12, 15, 0, 0, time.UTC)
	tradingStart := now.Add(-time.Hour)
	repository := &syncRepository{}
	service := candlesync.NewService(
		&targetLoader{targets: []assetdomain.MarketDataCollectionTarget{{
			AssetID:                 "asset-1",
			MarketCode:              "KRW-NEW",
			TradingSupportStartedAt: &tradingStart,
		}}},
		&candleSource{},
		repository,
		&recentCache{},
		now.Add(-24*time.Hour),
		1,
	)

	if err := service.Run(context.Background(), now); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	want := tradingStart.UTC().Truncate(time.Minute)
	if !repository.initializations[0].historyTargetFrom.Equal(want) {
		t.Fatalf("history target = %v, want %v", repository.initializations[0].historyTargetFrom, want)
	}
}

func TestServiceCachesOnlyCandlesInsideVerifiedWindow(t *testing.T) {
	now := time.Date(2026, 9, 7, 12, 15, 0, 0, time.UTC)
	anchor := now.Add(-time.Minute)
	inside := candledomain.MinuteCandle{OpenTime: anchor.Add(-time.Minute)}
	outside := candledomain.MinuteCandle{OpenTime: anchor.Add(-201 * time.Minute)}
	cache := &recentCache{}
	service := candlesync.NewService(
		&targetLoader{targets: []assetdomain.MarketDataCollectionTarget{{AssetID: "asset-1", MarketCode: "KRW-BTC"}}},
		&candleSource{candles: []candledomain.MinuteCandle{inside, outside}},
		&syncRepository{},
		cache,
		now.Add(-24*time.Hour),
		1,
	)

	if err := service.Run(context.Background(), now); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(cache.batches) != 1 || len(cache.batches[0]) != 1 {
		t.Fatalf("cache batches = %#v, want one in-range candle", cache.batches)
	}
	if cache.batches[0][0].AssetID != "asset-1" || cache.batches[0][0].IntervalMinutes != 1 {
		t.Fatalf("cached candle = %#v, want resolved asset and interval", cache.batches[0][0])
	}
}

func TestServiceDoesNotAdvanceFailedWindow(t *testing.T) {
	now := time.Date(2026, 9, 7, 12, 15, 0, 0, time.UTC)
	wantErr := errors.New("database unavailable")
	repository := &syncRepository{applyErr: wantErr}
	service := candlesync.NewService(
		&targetLoader{targets: []assetdomain.MarketDataCollectionTarget{{AssetID: "asset-1", MarketCode: "KRW-BTC"}}},
		&candleSource{},
		repository,
		&recentCache{},
		now.Add(-24*time.Hour),
		2,
	)

	err := service.Run(context.Background(), now)
	if !errors.Is(err, wantErr) {
		t.Fatalf("Run() error = %v, want %v", err, wantErr)
	}
	if len(repository.windows) != 1 {
		t.Fatalf("window count = %d, want one attempt", len(repository.windows))
	}
}
