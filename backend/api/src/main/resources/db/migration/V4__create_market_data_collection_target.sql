CREATE SCHEMA IF NOT EXISTS ops;

CREATE TABLE ops.market_data_collection_target (
    asset_id           UUID        NOT NULL,
    collection_status  VARCHAR(20) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_market_data_collection_target
        PRIMARY KEY (asset_id),
    CONSTRAINT fk_market_data_collection_target_asset
        FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_market_data_collection_target_status
        CHECK (collection_status IN ('ENABLED', 'DISABLED'))
);
