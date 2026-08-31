package store

import (
	"context"

	assetdomain "lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/asset/domain"
	"lab.ssafy.com/s15-fintech-finance-sub1/S15P21A107/backend/ingest/internal/common/exception"

	"github.com/jackc/pgx/v5/pgxpool"
)

const enabledCollectionTargetsQuery = `
SELECT
    asset.asset_id::text,
    asset.exchange_market_code
FROM ops.market_data_collection_target AS target
JOIN reference.asset AS asset
  ON asset.asset_id = target.asset_id
WHERE target.collection_status = 'ENABLED'
  AND asset.exchange = 'UPBIT'
  AND asset.trading_status = 'TRADING'
ORDER BY asset.exchange_market_code`

// CollectionTargetRepository loads the markets that the API enabled for collection.
type CollectionTargetRepository struct {
	pool *pgxpool.Pool
}

func NewCollectionTargetRepository(pool *pgxpool.Pool) *CollectionTargetRepository {
	return &CollectionTargetRepository{pool: pool}
}

func (r *CollectionTargetRepository) LoadEnabledTargets(
	ctx context.Context,
) ([]assetdomain.MarketDataCollectionTarget, error) {
	rows, err := r.pool.Query(ctx, enabledCollectionTargetsQuery)
	if err != nil {
		return nil, exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}
	defer rows.Close()

	targets := make([]assetdomain.MarketDataCollectionTarget, 0)
	for rows.Next() {
		var target assetdomain.MarketDataCollectionTarget
		if err := rows.Scan(&target.AssetID, &target.MarketCode); err != nil {
			return nil, exception.Wrap(exception.CodeDatabaseUnavailable, err)
		}
		targets = append(targets, target)
	}
	if err := rows.Err(); err != nil {
		return nil, exception.Wrap(exception.CodeDatabaseUnavailable, err)
	}

	return targets, nil
}
