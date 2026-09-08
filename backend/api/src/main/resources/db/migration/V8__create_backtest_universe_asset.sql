CREATE TABLE trading.backtest_universe_asset (
    backtest_id       UUID        NOT NULL,
    rebalance_at      TIMESTAMPTZ NOT NULL,
    asset_id          UUID        NOT NULL,
    selection_rank    SMALLINT    NULL,
    is_selected       BOOLEAN     NOT NULL,

    CONSTRAINT pk_backtest_universe_asset
        PRIMARY KEY (backtest_id, rebalance_at, asset_id),
    CONSTRAINT fk_backtest_universe_asset_run
        FOREIGN KEY (backtest_id)
        REFERENCES trading.backtest_run (backtest_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_backtest_universe_asset_asset
        FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_backtest_universe_asset_selection_rank
        CHECK (selection_rank IS NULL OR selection_rank > 0)
);
