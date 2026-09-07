package store

import (
	"context"
	"errors"
	"strings"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// integrityViolationPrefix is the SQLSTATE class PostgreSQL reports for integrity
// constraint violations. Those come from bad data rather than a failing database,
// so the caller must not retry them.
const integrityViolationPrefix = "23"

const upsertMinuteCandleStatement = `
INSERT INTO market_data.minute_candle (
    asset_id,
    interval_minutes,
    open_time,
    open_price,
    high_price,
    low_price,
    close_price,
    trade_volume,
    trade_amount,
    last_event_time
) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
ON CONFLICT (asset_id, interval_minutes, open_time) DO UPDATE SET
    open_price      = EXCLUDED.open_price,
    high_price      = EXCLUDED.high_price,
    low_price       = EXCLUDED.low_price,
    close_price     = EXCLUDED.close_price,
    trade_volume    = EXCLUDED.trade_volume,
    trade_amount    = EXCLUDED.trade_amount,
    last_event_time = EXCLUDED.last_event_time,
    updated_at      = CURRENT_TIMESTAMP`

// MinuteCandleRepository stores the candles finalized by the ingest pipeline.
type MinuteCandleRepository struct {
	pool *pgxpool.Pool
}

func NewMinuteCandleRepository(pool *pgxpool.Pool) *MinuteCandleRepository {
	return &MinuteCandleRepository{pool: pool}
}

// Upsert writes a finalized candle, replacing the row that shares its identity.
// Consumers redeliver records and the backfill re-reports corrected candles, so the
// primary key (asset_id, interval_minutes, open_time) keeps repeated writes idempotent.
func (r *MinuteCandleRepository) Upsert(ctx context.Context, candle candledomain.MinuteCandle) error {
	_, err := r.pool.Exec(
		ctx,
		upsertMinuteCandleStatement,
		candle.AssetID,
		candle.IntervalMinutes,
		candle.OpenTime,
		candle.OpenPrice,
		candle.HighPrice,
		candle.LowPrice,
		candle.ClosePrice,
		candle.TradeVolume,
		candle.TradeAmount,
		candle.LastEventTime,
	)
	if err != nil {
		return wrapWriteError(err)
	}

	return nil
}

func wrapWriteError(err error) error {
	var databaseError *pgconn.PgError
	if errors.As(err, &databaseError) && strings.HasPrefix(databaseError.Code, integrityViolationPrefix) {
		return exception.Wrap(exception.CodeDatabaseConstraintViolation, err)
	}

	return exception.Wrap(exception.CodeDatabaseUnavailable, err)
}
