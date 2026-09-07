//go:build integration

// 실제 PostgreSQL이 필요하며 Flyway 마이그레이션이 적용된 상태여야 한다.
// 실행: go test -tags=integration ./tests/store -v
package store_test

import (
	"context"
	"testing"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlesync"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"

	"github.com/shopspring/decimal"
)

func TestCandleSyncAppliesVerifiedWindowAtomically(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	ctx := context.Background()
	anchor := time.Date(2026, 9, 7, 12, 10, 0, 0, time.UTC)
	from := anchor.Add(-time.Minute)

	if _, err := store.NewMinuteCandleRepository(pool).Upsert(ctx, testCandle("108900", from)); err != nil {
		t.Fatalf("initial Upsert() error = %v", err)
	}
	syncRepository := store.NewCandleSyncRepository(pool)
	state, err := syncRepository.Initialize(
		ctx,
		testAssetID,
		testMarketCode,
		anchor.Add(-24*time.Hour),
		anchor,
	)
	if err != nil {
		t.Fatalf("Initialize() error = %v", err)
	}
	verified := testCandle("108950", from)
	if err := syncRepository.ApplyWindow(
		ctx,
		state,
		candlesync.DirectionHistory,
		from,
		anchor,
		[]candledomain.MinuteCandle{verified},
	); err != nil {
		t.Fatalf("ApplyWindow() error = %v", err)
	}

	var verifiedFrom time.Time
	var verifiedAt *time.Time
	var closePrice decimal.Decimal
	err = pool.QueryRow(
		ctx,
		`SELECT sync_state.verified_from, candle.verified_at, candle.close_price
           FROM ops.minute_candle_sync_state AS sync_state
           JOIN market_data.minute_candle AS candle
             ON candle.asset_id = sync_state.asset_id
            AND candle.interval_minutes = sync_state.interval_minutes
          WHERE sync_state.asset_id = $1
            AND candle.open_time = $2`,
		testAssetID,
		from,
	).Scan(&verifiedFrom, &verifiedAt, &closePrice)
	if err != nil {
		t.Fatalf("검증 결과 조회 실패: %v", err)
	}
	if !verifiedFrom.Equal(from) || verifiedAt == nil || closePrice.String() != "108950" {
		t.Fatalf(
			"verifiedFrom/verifiedAt/closePrice = %v/%v/%s, want %v/non-nil/108950",
			verifiedFrom,
			verifiedAt,
			closePrice,
			from,
		)
	}

	lateCandle := testCandle("1", from)
	stored, err := store.NewMinuteCandleRepository(pool).Upsert(ctx, lateCandle)
	if err != nil {
		t.Fatalf("delayed Upsert() error = %v", err)
	}
	if stored != nil {
		t.Fatalf("delayed Upsert() = %#v, want ignored verified candle", stored)
	}
}

func TestCandleSyncKeepsBoundaryWhenDatabaseOnlyCandleExists(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	ctx := context.Background()
	anchor := time.Date(2026, 9, 7, 12, 10, 0, 0, time.UTC)
	from := anchor.Add(-time.Minute)

	liveRepository := store.NewMinuteCandleRepository(pool)
	if _, err := liveRepository.Upsert(ctx, testCandle("108900", from)); err != nil {
		t.Fatalf("Upsert() error = %v", err)
	}
	syncRepository := store.NewCandleSyncRepository(pool)
	state, err := syncRepository.Initialize(
		ctx,
		testAssetID,
		testMarketCode,
		anchor.Add(-24*time.Hour),
		anchor,
	)
	if err != nil {
		t.Fatalf("Initialize() error = %v", err)
	}

	err = syncRepository.ApplyWindow(
		ctx,
		state,
		candlesync.DirectionHistory,
		from,
		anchor,
		nil,
	)
	if !exception.HasCode(err, exception.CodeCandleSyncMismatch) {
		t.Fatalf("ApplyWindow() error = %v, want code %s", err, exception.CodeCandleSyncMismatch)
	}

	var verifiedFrom time.Time
	if err := pool.QueryRow(
		ctx,
		`SELECT verified_from
           FROM ops.minute_candle_sync_state
          WHERE asset_id = $1 AND interval_minutes = 1`,
		testAssetID,
	).Scan(&verifiedFrom); err != nil {
		t.Fatalf("검증 범위 조회 실패: %v", err)
	}
	if !verifiedFrom.Equal(anchor) {
		t.Fatalf("VerifiedFrom = %v, want unchanged %v", verifiedFrom, anchor)
	}
}

func TestOnlyOneCandleSyncLockCanBeAcquired(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()

	first, acquired, err := store.TryAcquireCandleSyncLock(ctx, pool)
	if err != nil || !acquired {
		t.Fatalf("first lock = %v/%v, want acquired without error", acquired, err)
	}
	second, secondAcquired, err := store.TryAcquireCandleSyncLock(ctx, pool)
	if err != nil {
		t.Fatalf("second lock error = %v", err)
	}
	if second != nil || secondAcquired {
		t.Fatalf("second lock = %#v/%v, want unavailable", second, secondAcquired)
	}
	if err := first.Release(ctx); err != nil {
		t.Fatalf("Release() error = %v", err)
	}
}
