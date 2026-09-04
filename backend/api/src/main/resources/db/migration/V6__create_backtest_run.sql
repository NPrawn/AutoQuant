CREATE SCHEMA IF NOT EXISTS trading;

CREATE TABLE trading.backtest_run (
    backtest_id             UUID         NOT NULL,
    member_id               UUID         NOT NULL,
    strategy_id             BIGINT       NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    period_start            TIMESTAMPTZ  NOT NULL,
    period_end              TIMESTAMPTZ  NOT NULL,
    timeframe               VARCHAR(10)  NOT NULL,
    quote_currency          VARCHAR(10)  NOT NULL,
    initial_capital         NUMERIC(24, 8) NOT NULL,
    fee_rate                NUMERIC(12, 8) NOT NULL,
    slippage_bps            NUMERIC(12, 4) NOT NULL,
    rebalance_interval      VARCHAR(20)  NOT NULL,
    execution_price_rule    VARCHAR(30)  NOT NULL,
    engine_version          VARCHAR(50)  NOT NULL,
    market_data_version     VARCHAR(50)  NOT NULL,
    queued_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at              TIMESTAMPTZ  NULL,
    completed_at            TIMESTAMPTZ  NULL,
    failure_reason          TEXT         NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_backtest_run
        PRIMARY KEY (backtest_id),
    CONSTRAINT fk_backtest_run_member
        FOREIGN KEY (member_id)
        REFERENCES account.member (member_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_backtest_run_strategy
        FOREIGN KEY (strategy_id)
        REFERENCES strategy.strategy (strategy_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_backtest_run_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_backtest_run_period
        CHECK (period_start < period_end),
    CONSTRAINT ck_backtest_run_timeframe
        CHECK (timeframe IN ('1H', '4H', '1D')),
    CONSTRAINT ck_backtest_run_quote_currency
        CHECK (BTRIM(quote_currency) <> '' AND quote_currency = UPPER(quote_currency)),
    CONSTRAINT ck_backtest_run_initial_capital
        CHECK (initial_capital > 0),
    CONSTRAINT ck_backtest_run_fee_rate
        CHECK (fee_rate >= 0),
    CONSTRAINT ck_backtest_run_slippage_bps
        CHECK (slippage_bps >= 0),
    CONSTRAINT ck_backtest_run_rebalance_interval
        CHECK (rebalance_interval IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_backtest_run_execution_price_rule
        CHECK (execution_price_rule = 'NEXT_CANDLE_OPEN'),
    CONSTRAINT ck_backtest_run_failure_reason
        CHECK (status <> 'FAILED' OR (failure_reason IS NOT NULL AND BTRIM(failure_reason) <> '')),
    CONSTRAINT ck_backtest_run_completed_at
        CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE INDEX idx_backtest_run_member_id_status
    ON trading.backtest_run (member_id, status);

CREATE INDEX idx_backtest_run_strategy_id
    ON trading.backtest_run (strategy_id);
