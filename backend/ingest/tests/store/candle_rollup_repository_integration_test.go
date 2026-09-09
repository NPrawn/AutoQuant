//go:build integration

// Dedicated PostgreSQL with all Flyway migrations and Redis are required.
// TEST_DATABASE_URL / TEST_REDIS_ADDRESS; go test -tags=integration ./tests/store
package store_test

import (
	"context"
	"encoding/json"
	"os"
	"testing"
	"time"

	"github.com/shopspring/decimal"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlerollup"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlesync"
	ingestredis "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/redis"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"
)

func TestRollupCorrectionAndCache(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	ctx := context.Background()
	address := os.Getenv("TEST_REDIS_ADDRESS")
	if address == "" {
		t.Skip("TEST_REDIS_ADDRESS must point to a dedicated Redis")
	}
	client := ingestredis.NewClient(address)
	t.Cleanup(func() {
		for _, interval := range candlerollup.Intervals() {
			if err := client.Del(ctx, ingestredis.RecentCandlesKey(testAssetID, interval)).Err(); err != nil {
				t.Error(err)
			}
		}
		if err := client.Close(); err != nil {
			t.Error(err)
		}
	})
	base := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	source := store.NewMinuteCandleRepository(pool)
	for index := range 5 {
		if _, err := source.Upsert(ctx, testCandle("108900", base.Add(time.Duration(index)*time.Minute))); err != nil {
			t.Fatal(err)
		}
	}
	repository := store.NewCandleRollupRepository(pool)
	cache := ingestredis.NewRecentMinuteCandleRepository(client)
	service := candlerollup.NewService(repository, cache)
	if err := service.RunOnce(ctx, base.Add(25*time.Hour)); err != nil {
		t.Fatal(err)
	}
	corrected := testCandle("108950", base.Add(4*time.Minute))
	corrected.TradeVolume = decimal.NewFromInt(10)
	syncRepository := store.NewCandleSyncRepository(pool)
	state, err := syncRepository.Initialize(ctx, testAssetID, testMarketCode, base, base.Add(5*time.Minute))
	if err != nil {
		t.Fatal(err)
	}
	if err := syncRepository.ApplyWindow(ctx, state, candlesync.DirectionHistory, base.Add(4*time.Minute),
		base.Add(5*time.Minute), []candledomain.MinuteCandle{corrected}); err != nil {
		t.Fatal(err)
	}
	// Reconstruct the service: durable jobs must survive the previous process.
	if err := candlerollup.NewService(repository, cache).RunOnce(ctx, base.Add(25*time.Hour)); err != nil {
		t.Fatal(err)
	}
	var volume, closePrice decimal.Decimal
	if err := pool.QueryRow(ctx, `SELECT trade_volume, close_price FROM market_data.minute_candle
WHERE asset_id=$1 AND interval_minutes=5 AND open_time=$2`, testAssetID, base).Scan(&volume, &closePrice); err != nil {
		t.Fatal(err)
	}
	want := decimal.RequireFromString("14.93827156")
	if !volume.Equal(want) || closePrice.String() != "108950" {
		t.Fatalf("got %s/%s, want %s/108950", volume, closePrice, want)
	}
	entries, err := client.ZRange(ctx, ingestredis.RecentCandlesKey(testAssetID, 5), 0, -1).Result()
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 {
		t.Fatalf("got %d entries, want 1", len(entries))
	}
	var payload map[string]any
	if err := json.Unmarshal([]byte(entries[0]), &payload); err != nil {
		t.Fatal(err)
	}
	if payload["trade_volume"] != want.String() || payload["close_price"] != "108950" {
		t.Fatalf("got %v, want corrected values", payload)
	}
}

func TestRollupDoesNotLoseConcurrentCorrection(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	ctx := context.Background()
	base := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	source := store.NewMinuteCandleRepository(pool)
	if _, err := source.Upsert(ctx, testCandle("108900", base)); err != nil {
		t.Fatal(err)
	}
	repository := store.NewCandleRollupRepository(pool)
	job, err := repository.Next(ctx, base.Add(25*time.Hour))
	if err != nil || job == nil {
		t.Fatalf("Next got %v/%v, want job", job, err)
	}
	if _, err := source.Upsert(ctx, testCandle("108950", base)); err != nil {
		t.Fatal(err)
	}
	if err := repository.Complete(ctx, *job); err != nil {
		t.Fatal(err)
	}
	next, err := repository.Next(ctx, base.Add(25*time.Hour))
	if err != nil || next == nil || next.Revision <= job.Revision {
		t.Fatalf("got %v/%v, want newer revision", next, err)
	}
}

func TestRollupQueuesOnlyCommittedSourceAndDefersOpenBuckets(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	ctx := context.Background()
	base := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC)
	tx, err := pool.Begin(ctx)
	if err != nil {
		t.Fatal(err)
	}
	_, err = tx.Exec(ctx, `INSERT INTO market_data.minute_candle(asset_id,interval_minutes,open_time,
open_price,high_price,low_price,close_price,trade_volume,trade_amount,last_event_time)
VALUES($1,1,$2,100,100,100,100,1,100,$2)`, testAssetID, base)
	if err != nil {
		t.Fatal(err)
	}
	if err := tx.Rollback(ctx); err != nil {
		t.Fatal(err)
	}
	repository := store.NewCandleRollupRepository(pool)
	job, err := repository.Next(ctx, base.Add(25*time.Hour))
	if err != nil || job != nil {
		t.Fatalf("got %v/%v, want no rolled-back job", job, err)
	}
	if _, err := store.NewMinuteCandleRepository(pool).Upsert(ctx, testCandle("108900", base)); err != nil {
		t.Fatal(err)
	}
	job, err = repository.Next(ctx, base.Add(time.Minute))
	if err != nil || job != nil {
		t.Fatalf("got %v/%v, want no open bucket", job, err)
	}
}

func TestRollupExclusiveAcrossInstances(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	first, second := store.NewCandleRollupRepository(pool), store.NewCandleRollupRepository(pool)
	ranSecond := false
	err := first.Exclusive(ctx, func() error { return second.Exclusive(ctx, func() error { ranSecond = true; return nil }) })
	if err != nil || ranSecond {
		t.Fatalf("got %v, second=%v, want nil/false", err, ranSecond)
	}
	if err := second.Exclusive(ctx, func() error { ranSecond = true; return nil }); err != nil || !ranSecond {
		t.Fatalf("got %v, second=%v, want nil/true", err, ranSecond)
	}
}
