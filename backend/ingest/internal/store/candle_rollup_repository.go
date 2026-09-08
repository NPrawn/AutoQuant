package store

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlerollup"
)

type CandleRollupRepository struct{ pool *pgxpool.Pool }

func NewCandleRollupRepository(pool *pgxpool.Pool) *CandleRollupRepository {
	return &CandleRollupRepository{pool: pool}
}

// Keep the session lock until DB and cache have both been updated.
func (r *CandleRollupRepository) Exclusive(ctx context.Context, work func() error) (result error) {
	lock, acquired, err := tryAcquireCandleLock(ctx, r.pool, 171002)
	if err != nil {
		return err
	}
	if !acquired {
		return nil
	}
	defer func() {
		closeCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		result = errors.Join(result, lock.Release(closeCtx))
	}()
	return work()
}

func (r *CandleRollupRepository) Next(ctx context.Context, cutoff time.Time) (*candlerollup.Job, error) {
	var job candlerollup.Job
	err := r.pool.QueryRow(ctx, `SELECT asset_id::text, interval_minutes, open_time, revision
FROM ops.candle_rollup_job
WHERE open_time + make_interval(mins => interval_minutes) <= $1
ORDER BY open_time DESC, asset_id, interval_minutes LIMIT 1`, cutoff).
		Scan(&job.AssetID, &job.IntervalMinutes, &job.OpenTime, &job.Revision)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, wrapWriteError(err)
	}
	return &job, nil
}

func (r *CandleRollupRepository) LoadMinutes(ctx context.Context, assetID string, from, to time.Time) ([]candledomain.MinuteCandle, error) {
	rows, err := r.pool.Query(ctx, `SELECT open_time, open_price, high_price, low_price, close_price,
trade_volume, trade_amount, last_event_time FROM market_data.minute_candle
WHERE asset_id=$1 AND interval_minutes=1 AND open_time >= $2 AND open_time < $3 ORDER BY open_time`, assetID, from, to)
	if err != nil {
		return nil, wrapWriteError(err)
	}
	defer rows.Close()
	var result []candledomain.MinuteCandle
	for rows.Next() {
		value := candledomain.MinuteCandle{AssetID: assetID, IntervalMinutes: 1}
		if err := rows.Scan(&value.OpenTime, &value.OpenPrice, &value.HighPrice, &value.LowPrice, &value.ClosePrice, &value.TradeVolume, &value.TradeAmount, &value.LastEventTime); err != nil {
			return nil, wrapWriteError(err)
		}
		result = append(result, value)
	}
	if err := rows.Err(); err != nil {
		return nil, wrapWriteError(err)
	}
	return result, nil
}

func (r *CandleRollupRepository) Save(ctx context.Context, value candledomain.MinuteCandle) error {
	_, err := r.pool.Exec(ctx, `INSERT INTO market_data.minute_candle
(asset_id, interval_minutes, open_time, open_price, high_price, low_price, close_price, trade_volume, trade_amount, last_event_time)
VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
ON CONFLICT (asset_id, interval_minutes, open_time) DO UPDATE SET
open_price=EXCLUDED.open_price, high_price=EXCLUDED.high_price, low_price=EXCLUDED.low_price,
close_price=EXCLUDED.close_price, trade_volume=EXCLUDED.trade_volume, trade_amount=EXCLUDED.trade_amount,
last_event_time=EXCLUDED.last_event_time, updated_at=CURRENT_TIMESTAMP`,
		value.AssetID, value.IntervalMinutes, value.OpenTime, value.OpenPrice, value.HighPrice, value.LowPrice,
		value.ClosePrice, value.TradeVolume, value.TradeAmount, value.LastEventTime)
	if err != nil {
		return wrapWriteError(err)
	}
	return nil
}

func (r *CandleRollupRepository) Complete(ctx context.Context, job candlerollup.Job) error {
	_, err := r.pool.Exec(ctx, `DELETE FROM ops.candle_rollup_job
WHERE asset_id=$1 AND interval_minutes=$2 AND open_time=$3 AND revision=$4`, job.AssetID, job.IntervalMinutes, job.OpenTime, job.Revision)
	if err != nil {
		return wrapWriteError(err)
	}
	return nil
}
