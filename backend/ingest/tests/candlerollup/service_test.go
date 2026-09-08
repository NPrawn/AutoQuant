package candlerollup_test

import (
	"context"
	"errors"
	"testing"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlerollup"
)

type repositoryFake struct {
	job       *candlerollup.Job
	completed bool
	saved     *candledomain.MinuteCandle
}

func (r *repositoryFake) Exclusive(_ context.Context, run func() error) error { return run() }
func (r *repositoryFake) Next(_ context.Context, _ time.Time) (*candlerollup.Job, error) {
	return r.job, nil
}
func (r *repositoryFake) LoadMinutes(_ context.Context, asset string, from, to time.Time) ([]candledomain.MinuteCandle, error) {
	return []candledomain.MinuteCandle{minute(asset, from, 100, 1)}, nil
}
func (r *repositoryFake) Save(_ context.Context, value candledomain.MinuteCandle) error {
	r.saved = &value
	return nil
}
func (r *repositoryFake) Complete(_ context.Context, _ candlerollup.Job) error {
	r.completed = true
	r.job = nil
	return nil
}

type cacheFake struct {
	err   error
	saved []candledomain.MinuteCandle
}

func (c *cacheFake) SaveBatch(_ context.Context, values []candledomain.MinuteCandle) error {
	c.saved = values
	return c.err
}

func TestServiceRetriesFailedCacheAfterRestart(t *testing.T) {
	ctx := context.Background()
	unavailable := errors.New("redis unavailable")
	repository := &repositoryFake{job: &candlerollup.Job{AssetID: "BTC", IntervalMinutes: 5, OpenTime: time.Now().UTC().Truncate(time.Hour), Revision: 1}}
	cache := &cacheFake{err: unavailable}
	err := candlerollup.NewService(repository, cache).RunOnce(ctx, time.Now())
	if !errors.Is(err, unavailable) || repository.completed {
		t.Fatalf("got %v / completed=%v, want Redis error / false", err, repository.completed)
	}
	cache.err = nil
	if err := candlerollup.NewService(repository, cache).RunOnce(ctx, time.Now()); err != nil {
		t.Fatal(err)
	}
	if !repository.completed || repository.saved.TradeVolume.String() != "1" || len(cache.saved) != 1 {
		t.Fatalf("got completed=%v, saved=%+v, want completed, volume=1", repository.completed, repository.saved)
	}
}
