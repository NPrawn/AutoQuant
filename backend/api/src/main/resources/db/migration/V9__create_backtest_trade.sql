CREATE TABLE trading.backtest_trade (
    trade_id          UUID          NOT NULL,
    backtest_id       UUID          NOT NULL,
    asset_id          UUID          NOT NULL,
    position_side     VARCHAR(10)   NOT NULL,
    side              VARCHAR(4)    NOT NULL,
    signal_at         TIMESTAMPTZ   NOT NULL,
    filled_at         TIMESTAMPTZ   NOT NULL,
    executed_price    NUMERIC(24,8) NOT NULL,
    quantity          NUMERIC(24,8) NOT NULL,
    notional          NUMERIC(24,8) NOT NULL,
    fee_amount        NUMERIC(24,8) NOT NULL,
    slippage_amount   NUMERIC(24,8) NOT NULL,
    reason            VARCHAR(100)  NULL,

    CONSTRAINT pk_backtest_trade
        PRIMARY KEY (trade_id),

    CONSTRAINT fk_backtest_trade_run
        FOREIGN KEY (backtest_id)
            REFERENCES trading.backtest_run (backtest_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_backtest_trade_asset
        FOREIGN KEY (asset_id)
            REFERENCES reference.asset (asset_id)
            ON DELETE RESTRICT,

    CONSTRAINT ck_backtest_trade_position_side
        CHECK (position_side IN ('LONG', 'SHORT')),

    CONSTRAINT ck_backtest_trade_side
        CHECK (side IN ('BUY', 'SELL')),

    CONSTRAINT ck_backtest_trade_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_backtest_trade_notional
        CHECK (notional >= 0),

    CONSTRAINT ck_backtest_trade_fee_amount
        CHECK (fee_amount >= 0),

    CONSTRAINT ck_backtest_trade_slippage_amount
        CHECK (slippage_amount >= 0),

    CONSTRAINT ck_backtest_trade_signal_filled_time
        CHECK (signal_at < filled_at)
);

CREATE INDEX idx_backtest_trade_backtest_id_filled_at
    ON trading.backtest_trade (backtest_id, filled_at);