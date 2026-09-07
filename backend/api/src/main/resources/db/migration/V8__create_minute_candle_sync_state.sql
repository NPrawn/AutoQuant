ALTER TABLE market_data.minute_candle
    ADD COLUMN verified_at TIMESTAMPTZ NULL;

CREATE SCHEMA IF NOT EXISTS ops;

CREATE TABLE ops.minute_candle_sync_state (
    asset_id                 UUID        NOT NULL,
    interval_minutes        SMALLINT    NOT NULL,
    history_target_from     TIMESTAMPTZ NOT NULL,
    verified_from           TIMESTAMPTZ NOT NULL,
    verified_to             TIMESTAMPTZ NOT NULL,
    history_updated_at      TIMESTAMPTZ NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_minute_candle_sync_state
        PRIMARY KEY (asset_id, interval_minutes),
    CONSTRAINT fk_minute_candle_sync_state_asset
        FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_minute_candle_sync_state_interval_positive
        CHECK (interval_minutes > 0),
    CONSTRAINT ck_minute_candle_sync_state_range
        CHECK (verified_from <= verified_to)
);

