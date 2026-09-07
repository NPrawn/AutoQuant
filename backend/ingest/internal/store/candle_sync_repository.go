package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	candledomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candle/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/candlesync"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const initializeCandleSyncStateStatement = `
INSERT INTO ops.minute_candle_sync_state (
    asset_id,
    interval_minutes,
    history_target_from,
    verified_from,
    verified_to
) VALUES ($1, $2, $3, $4, $4)
ON CONFLICT (asset_id, interval_minutes) DO UPDATE SET
    history_target_from = LEAST(
        ops.minute_candle_sync_state.history_target_from,
        EXCLUDED.history_target_from
    ),
    updated_at = CURRENT_TIMESTAMP
RETURNING
    asset_id::text,
    interval_minutes,
    history_target_from,
    verified_from,
    verified_to,
    history_updated_at`

const authoritativeMinuteCandleUpsertStatement = `
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
    last_event_time,
    verified_at
) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, CURRENT_TIMESTAMP)
ON CONFLICT (asset_id, interval_minutes, open_time) DO UPDATE SET
    open_price      = EXCLUDED.open_price,
    high_price      = EXCLUDED.high_price,
    low_price       = EXCLUDED.low_price,
    close_price     = EXCLUDED.close_price,
    trade_volume    = EXCLUDED.trade_volume,
    trade_amount    = EXCLUDED.trade_amount,
    last_event_time = EXCLUDED.last_event_time,
    verified_at     = CURRENT_TIMESTAMP,
    updated_at      = CURRENT_TIMESTAMP`

// CandleSyncRepository applies Upbit candle corrections and advances their
// verified range in one PostgreSQL transaction.
type CandleSyncRepository struct {
	pool *pgxpool.Pool
}

func NewCandleSyncRepository(pool *pgxpool.Pool) *CandleSyncRepository {
	return &CandleSyncRepository{pool: pool}
}

func (r *CandleSyncRepository) Initialize(
	ctx context.Context,
	assetID string,
	marketCode string,
	historyTargetFrom time.Time,
	anchor time.Time,
) (candlesync.State, error) {
	state := candlesync.State{MarketCode: marketCode}
	err := r.pool.QueryRow(
		ctx,
		initializeCandleSyncStateStatement,
		assetID,
		candlesync.IntervalMinutes,
		historyTargetFrom,
		anchor,
	).Scan(
		&state.AssetID,
		&state.IntervalMinutes,
		&state.HistoryTargetFrom,
		&state.VerifiedFrom,
		&state.VerifiedTo,
		&state.HistoryUpdatedAt,
	)
	if err != nil {
		return candlesync.State{}, exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}

	return state, nil
}

func (r *CandleSyncRepository) ApplyWindow(
	ctx context.Context,
	state candlesync.State,
	direction candlesync.Direction,
	from time.Time,
	to time.Time,
	candles []candledomain.MinuteCandle,
) (resultErr error) {
	tx, err := r.pool.BeginTx(ctx, pgx.TxOptions{})
	if err != nil {
		return exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}
	committed := false
	defer func() {
		if committed {
			return
		}
		if rollbackErr := tx.Rollback(ctx); rollbackErr != nil {
			resultErr = errors.Join(
				resultErr,
				exception.Wrap(exception.CodeDatabaseUnavailable, rollbackErr),
			)
		}
	}()

	if err := ensureNoDatabaseOnlyCandles(ctx, tx, state, from, to, candles); err != nil {
		return err
	}
	if err := upsertVerifiedCandles(ctx, tx, candles); err != nil {
		return err
	}
	if err := advanceVerifiedRange(ctx, tx, state, direction, from, to); err != nil {
		return err
	}
	if err := tx.Commit(ctx); err != nil {
		return exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}
	committed = true

	return nil
}

func ensureNoDatabaseOnlyCandles(
	ctx context.Context,
	tx pgx.Tx,
	state candlesync.State,
	from time.Time,
	to time.Time,
	candles []candledomain.MinuteCandle,
) error {
	remoteOpenTimes := make(map[int64]struct{}, len(candles))
	for _, candle := range candles {
		remoteOpenTimes[candle.OpenTime.UnixNano()] = struct{}{}
	}

	rows, err := tx.Query(
		ctx,
		`SELECT open_time
           FROM market_data.minute_candle
          WHERE asset_id = $1
            AND interval_minutes = $2
            AND open_time >= $3
            AND open_time < $4`,
		state.AssetID,
		state.IntervalMinutes,
		from,
		to,
	)
	if err != nil {
		return exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}
	defer rows.Close()

	for rows.Next() {
		var openTime time.Time
		if err := rows.Scan(&openTime); err != nil {
			return exception.Wrap(exception.CodeDatabaseUnavailable, err)
		}
		if _, exists := remoteOpenTimes[openTime.UnixNano()]; !exists {
			return exception.Wrap(
				exception.CodeCandleSyncMismatch,
				fmt.Errorf("market %s has database-only candle at %s", state.MarketCode, openTime.UTC()),
			)
		}
	}
	if err := rows.Err(); err != nil {
		return exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}

	return nil
}

func upsertVerifiedCandles(
	ctx context.Context,
	tx pgx.Tx,
	candles []candledomain.MinuteCandle,
) error {
	batch := &pgx.Batch{}
	for _, candle := range candles {
		batch.Queue(
			authoritativeMinuteCandleUpsertStatement,
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
	}
	if batch.Len() == 0 {
		return nil
	}

	results := tx.SendBatch(ctx, batch)
	for range candles {
		if _, err := results.Exec(); err != nil {
			closeErr := results.Close()
			if closeErr != nil {
				return errors.Join(wrapWriteError(err), wrapWriteError(closeErr))
			}
			return wrapWriteError(err)
		}
	}
	if err := results.Close(); err != nil {
		return wrapWriteError(err)
	}

	return nil
}

func advanceVerifiedRange(
	ctx context.Context,
	tx pgx.Tx,
	state candlesync.State,
	direction candlesync.Direction,
	from time.Time,
	to time.Time,
) error {
	var statement string
	switch direction {
	case candlesync.DirectionHistory:
		statement = `UPDATE ops.minute_candle_sync_state
            SET verified_from = $3,
                history_updated_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
          WHERE asset_id = $1
            AND interval_minutes = $2
            AND verified_from = $4`
	case candlesync.DirectionLatest:
		statement = `UPDATE ops.minute_candle_sync_state
            SET verified_to = $4,
                updated_at = CURRENT_TIMESTAMP
          WHERE asset_id = $1
            AND interval_minutes = $2
            AND verified_to = $3`
	default:
		return exception.Wrap(
			exception.CodeCandleInvalidPayload,
			fmt.Errorf("unknown candle sync direction %q", direction),
		)
	}

	commandTag, err := tx.Exec(
		ctx,
		statement,
		state.AssetID,
		state.IntervalMinutes,
		from,
		to,
	)
	if err != nil {
		return exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}
	if commandTag.RowsAffected() != 1 {
		return exception.Wrap(
			exception.CodeCandleSyncMismatch,
			fmt.Errorf("verified boundary changed for market %s", state.MarketCode),
		)
	}

	return nil
}
