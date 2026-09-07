package store

import (
	"context"
	"errors"

	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/jackc/pgx/v5/pgxpool"
)

const candleSyncAdvisoryLockID = int64(171001)

// CandleSyncLock holds one PostgreSQL session advisory lock for a complete run.
type CandleSyncLock struct {
	connection *pgxpool.Conn
}

func TryAcquireCandleSyncLock(
	ctx context.Context,
	pool *pgxpool.Pool,
) (*CandleSyncLock, bool, error) {
	connection, err := pool.Acquire(ctx)
	if err != nil {
		return nil, false, exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}

	var acquired bool
	if err := connection.QueryRow(
		ctx,
		"SELECT pg_try_advisory_lock($1)",
		candleSyncAdvisoryLockID,
	).Scan(&acquired); err != nil {
		closeErr := connection.Conn().Close(ctx)
		connection.Release()
		return nil, false, errors.Join(
			exception.Wrap(exception.CodeDatabaseUnavailable, err),
			wrapConnectionCloseError(closeErr),
		)
	}
	if !acquired {
		connection.Release()
		return nil, false, nil
	}

	return &CandleSyncLock{connection: connection}, true, nil
}

func (l *CandleSyncLock) Release(ctx context.Context) error {
	if l == nil || l.connection == nil {
		return nil
	}
	connection := l.connection
	l.connection = nil
	defer connection.Release()

	var released bool
	if err := connection.QueryRow(
		ctx,
		"SELECT pg_advisory_unlock($1)",
		candleSyncAdvisoryLockID,
	).Scan(&released); err != nil {
		return errors.Join(
			exception.Wrap(exception.CodeDatabaseUnavailable, err),
			wrapConnectionCloseError(connection.Conn().Close(ctx)),
		)
	}
	if !released {
		return errors.Join(
			exception.New(exception.CodeDatabaseUnavailable),
			wrapConnectionCloseError(connection.Conn().Close(ctx)),
		)
	}

	return nil
}

func wrapConnectionCloseError(err error) error {
	if err == nil {
		return nil
	}
	return exception.Wrap(exception.CodeDatabaseUnavailable, err)
}
