//go:build integration

// 실제 PostgreSQL이 필요하며 Flyway 마이그레이션이 적용된 상태여야 한다.
// 실행: docker compose -f infra/docker-compose.yml up -d postgres
//
//	go test -tags=integration ./tests/store -v
package store_test

import (
	"context"
	"os"
	"testing"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/store"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/shopspring/decimal"
)

const (
	testAssetID     = "00000000-0000-0000-0000-0000000000c1"
	testMarketCode  = "KRW-TSTC"
	testIntervalMin = int16(1)
)

func databaseURL() string {
	if url := os.Getenv("TEST_DATABASE_URL"); url != "" {
		return url
	}

	return "postgres://a107:a107@localhost:5432/a107?sslmode=disable"
}

func newTestPool(t *testing.T) *pgxpool.Pool {
	t.Helper()

	ctx := context.Background()
	pool, err := store.NewPool(ctx, databaseURL())
	if err != nil {
		t.Fatalf("NewPool() returned error: %v", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		t.Fatalf("PostgreSQL에 연결할 수 없다: %v", err)
	}
	t.Cleanup(pool.Close)

	return pool
}

func prepareTestAsset(t *testing.T, pool *pgxpool.Pool) {
	t.Helper()

	ctx := context.Background()
	cleanup := func() {
		if _, err := pool.Exec(ctx, `DELETE FROM market_data.minute_candle WHERE asset_id = $1`, testAssetID); err != nil {
			t.Fatalf("테스트 봉 정리 실패: %v", err)
		}
		if _, err := pool.Exec(ctx, `DELETE FROM reference.asset WHERE asset_id = $1`, testAssetID); err != nil {
			t.Fatalf("테스트 자산 정리 실패: %v", err)
		}
	}
	cleanup()

	_, err := pool.Exec(ctx, `
		INSERT INTO reference.asset (
			asset_id, exchange, exchange_market_code,
			base_currency_code, quote_currency_code,
			korean_name, english_name, trading_status
		) VALUES ($1, 'UPBIT', $2, 'TSTC', 'KRW', '테스트코인', 'Test Coin', 'TRADING')`,
		testAssetID, testMarketCode,
	)
	if err != nil {
		t.Fatalf("테스트 자산 생성 실패: %v", err)
	}
	t.Cleanup(cleanup)
}

func testCandle(closePrice string, openTime time.Time) candledomain.MinuteCandle {
	return candledomain.MinuteCandle{
		AssetID:         testAssetID,
		IntervalMinutes: testIntervalMin,
		OpenTime:        openTime,
		OpenPrice:       decimal.RequireFromString("108800"),
		HighPrice:       decimal.RequireFromString("109000"),
		LowPrice:        decimal.RequireFromString("108700"),
		ClosePrice:      decimal.RequireFromString(closePrice),
		TradeVolume:     decimal.RequireFromString("1.23456789"),
		TradeAmount:     decimal.RequireFromString("134321.98765432"),
		LastEventTime:   openTime.Add(30 * time.Second),
	}
}

func countCandles(t *testing.T, pool *pgxpool.Pool) int {
	t.Helper()

	var count int
	err := pool.QueryRow(
		context.Background(),
		`SELECT count(*) FROM market_data.minute_candle WHERE asset_id = $1`,
		testAssetID,
	).Scan(&count)
	if err != nil {
		t.Fatalf("봉 개수 조회 실패: %v", err)
	}

	return count
}

func TestUpsertStoresCandleOnce(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	repository := store.NewMinuteCandleRepository(pool)
	openTime := time.Date(2026, 9, 4, 10, 3, 0, 0, time.UTC)

	if err := repository.Upsert(context.Background(), testCandle("108900", openTime)); err != nil {
		t.Fatalf("첫 Upsert returned error: %v", err)
	}
	if err := repository.Upsert(context.Background(), testCandle("108900", openTime)); err != nil {
		t.Fatalf("두 번째 Upsert returned error: %v", err)
	}

	if got := countCandles(t, pool); got != 1 {
		t.Fatalf("저장된 봉 개수 = %d, want 1", got)
	}
}

func TestUpsertReplacesCorrectedCandle(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	repository := store.NewMinuteCandleRepository(pool)
	openTime := time.Date(2026, 9, 4, 10, 4, 0, 0, time.UTC)

	if err := repository.Upsert(context.Background(), testCandle("108900", openTime)); err != nil {
		t.Fatalf("첫 Upsert returned error: %v", err)
	}
	if err := repository.Upsert(context.Background(), testCandle("108950", openTime)); err != nil {
		t.Fatalf("보정 Upsert returned error: %v", err)
	}

	var closePrice decimal.Decimal
	err := pool.QueryRow(
		context.Background(),
		`SELECT close_price FROM market_data.minute_candle
		  WHERE asset_id = $1 AND interval_minutes = $2 AND open_time = $3`,
		testAssetID, testIntervalMin, openTime,
	).Scan(&closePrice)
	if err != nil {
		t.Fatalf("종가 조회 실패: %v", err)
	}

	if got := closePrice.String(); got != "108950" {
		t.Fatalf("ClosePrice = %s, want 108950", got)
	}
}

func TestUpsertKeepsAmountPrecision(t *testing.T) {
	pool := newTestPool(t)
	prepareTestAsset(t, pool)
	repository := store.NewMinuteCandleRepository(pool)
	openTime := time.Date(2026, 9, 4, 10, 5, 0, 0, time.UTC)

	if err := repository.Upsert(context.Background(), testCandle("108900", openTime)); err != nil {
		t.Fatalf("Upsert returned error: %v", err)
	}

	var tradeVolume decimal.Decimal
	err := pool.QueryRow(
		context.Background(),
		`SELECT trade_volume FROM market_data.minute_candle
		  WHERE asset_id = $1 AND interval_minutes = $2 AND open_time = $3`,
		testAssetID, testIntervalMin, openTime,
	).Scan(&tradeVolume)
	if err != nil {
		t.Fatalf("거래량 조회 실패: %v", err)
	}

	if !tradeVolume.Equal(decimal.RequireFromString("1.23456789")) {
		t.Fatalf("TradeVolume = %s, want 1.23456789", tradeVolume)
	}
}

func TestUpsertRejectsUnknownAsset(t *testing.T) {
	pool := newTestPool(t)
	repository := store.NewMinuteCandleRepository(pool)

	candle := testCandle("108900", time.Date(2026, 9, 4, 10, 6, 0, 0, time.UTC))
	candle.AssetID = "00000000-0000-0000-0000-0000000000ff"

	err := repository.Upsert(context.Background(), candle)
	if !exception.HasCode(err, exception.CodeDatabaseConstraintViolation) {
		t.Fatalf("HasCode(err, %s) = false, want true (err = %v)", exception.CodeDatabaseConstraintViolation, err)
	}
}
