CREATE TABLE trading.simulation_run (
    simulation_id          UUID          NOT NULL,
    member_id              UUID          NOT NULL,
    strategy_id            BIGINT        NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    timeframe              VARCHAR(10)   NOT NULL,
    quote_currency         VARCHAR(10)   NOT NULL,
    initial_capital        NUMERIC(24,8) NOT NULL,
    cash_balance           NUMERIC(24,8) NOT NULL,
    fee_rate               NUMERIC(12,8) NOT NULL,
    slippage_bps           NUMERIC(12,4) NOT NULL,
    rebalance_interval     VARCHAR(20)   NOT NULL,
    execution_price_rule   VARCHAR(30)   NOT NULL,
    engine_version         VARCHAR(50)   NOT NULL,
    started_at             TIMESTAMPTZ   NULL,
    stopped_at             TIMESTAMPTZ   NULL,
    last_candle_at         TIMESTAMPTZ   NULL,
    last_rebalance_at      TIMESTAMPTZ   NULL,
    failure_reason         TEXT          NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_simulation_run PRIMARY KEY (simulation_id),
    CONSTRAINT fk_simulation_run_member FOREIGN KEY (member_id)
        REFERENCES account.member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_simulation_run_strategy FOREIGN KEY (strategy_id)
        REFERENCES strategy.strategy (strategy_id) ON DELETE RESTRICT,
    CONSTRAINT ck_simulation_run_status CHECK (status IN ('CREATED', 'RUNNING', 'STOPPED', 'FAILED')),
    CONSTRAINT ck_simulation_run_timeframe CHECK (timeframe IN ('1H', '4H', '1D')),
    CONSTRAINT ck_simulation_run_quote_currency CHECK (quote_currency = 'KRW'),
    CONSTRAINT ck_simulation_run_initial_capital CHECK (initial_capital > 0),
    CONSTRAINT ck_simulation_run_cash_balance CHECK (cash_balance >= 0),
    CONSTRAINT ck_simulation_run_fee_rate CHECK (fee_rate >= 0 AND fee_rate < 1),
    CONSTRAINT ck_simulation_run_slippage_bps CHECK (slippage_bps >= 0),
    CONSTRAINT ck_simulation_run_rebalance_interval CHECK (rebalance_interval IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT ck_simulation_run_execution_price_rule CHECK (execution_price_rule = 'NEXT_CANDLE_OPEN'),
    CONSTRAINT ck_simulation_run_failure_reason CHECK (
        (status = 'FAILED' AND failure_reason IS NOT NULL AND BTRIM(failure_reason) <> '')
        OR (status <> 'FAILED' AND failure_reason IS NULL)
    ),
    CONSTRAINT ck_simulation_run_timeline CHECK (
        (started_at IS NULL OR stopped_at IS NULL OR started_at <= stopped_at)
        AND (last_candle_at IS NULL OR started_at IS NOT NULL)
        AND (last_rebalance_at IS NULL OR last_candle_at IS NOT NULL)
    ),
    CONSTRAINT ck_simulation_run_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_simulation_run_member_id_status
    ON trading.simulation_run (member_id, status);

CREATE INDEX idx_simulation_run_strategy_id
    ON trading.simulation_run (strategy_id);

CREATE TABLE trading.simulation_universe_asset (
    simulation_id   UUID        NOT NULL,
    rebalance_at    TIMESTAMPTZ NOT NULL,
    asset_id        UUID        NOT NULL,
    selection_rank  SMALLINT    NULL,
    is_selected     BOOLEAN     NOT NULL,

    CONSTRAINT pk_simulation_universe_asset PRIMARY KEY (simulation_id, rebalance_at, asset_id),
    CONSTRAINT fk_simulation_universe_asset_run FOREIGN KEY (simulation_id)
        REFERENCES trading.simulation_run (simulation_id) ON DELETE CASCADE,
    CONSTRAINT fk_simulation_universe_asset_asset FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id) ON DELETE RESTRICT,
    CONSTRAINT ck_simulation_universe_asset_selection_rank CHECK (selection_rank IS NULL OR selection_rank > 0)
);

CREATE TABLE trading.simulation_position (
    simulation_id        UUID          NOT NULL,
    asset_id             UUID          NOT NULL,
    position_side        VARCHAR(10)   NOT NULL,
    quantity             NUMERIC(24,8) NOT NULL,
    average_entry_price  NUMERIC(24,8) NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_simulation_position PRIMARY KEY (simulation_id, asset_id, position_side),
    CONSTRAINT fk_simulation_position_run FOREIGN KEY (simulation_id)
        REFERENCES trading.simulation_run (simulation_id) ON DELETE CASCADE,
    CONSTRAINT fk_simulation_position_asset FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id) ON DELETE RESTRICT,
    CONSTRAINT ck_simulation_position_side CHECK (position_side IN ('LONG', 'SHORT')),
    CONSTRAINT ck_simulation_position_quantity CHECK (quantity > 0),
    CONSTRAINT ck_simulation_position_average_entry_price CHECK (average_entry_price > 0)
);

CREATE TABLE trading.simulation_trade (
    trade_id          UUID          NOT NULL,
    simulation_id     UUID          NOT NULL,
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

    CONSTRAINT pk_simulation_trade PRIMARY KEY (trade_id),
    CONSTRAINT fk_simulation_trade_run FOREIGN KEY (simulation_id)
        REFERENCES trading.simulation_run (simulation_id) ON DELETE CASCADE,
    CONSTRAINT fk_simulation_trade_asset FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id) ON DELETE RESTRICT,
    CONSTRAINT ck_simulation_trade_position_side CHECK (position_side IN ('LONG', 'SHORT')),
    CONSTRAINT ck_simulation_trade_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_simulation_trade_quantity CHECK (quantity > 0),
    CONSTRAINT ck_simulation_trade_executed_price CHECK (executed_price > 0),
    CONSTRAINT ck_simulation_trade_notional CHECK (notional >= 0),
    CONSTRAINT ck_simulation_trade_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT ck_simulation_trade_slippage_amount CHECK (slippage_amount >= 0),
    CONSTRAINT ck_simulation_trade_signal_filled_time CHECK (signal_at <= filled_at)
);

CREATE INDEX idx_simulation_trade_simulation_id_filled_at
    ON trading.simulation_trade (simulation_id, filled_at);

CREATE TABLE trading.simulation_equity (
    simulation_id       UUID          NOT NULL,
    valuation_at        TIMESTAMPTZ   NOT NULL,
    total_asset_value   NUMERIC(24,8) NOT NULL,
    cash_balance        NUMERIC(24,8) NOT NULL,
    return_rate         NUMERIC(14,8) NOT NULL,
    drawdown_rate       NUMERIC(14,8) NOT NULL,

    CONSTRAINT pk_simulation_equity PRIMARY KEY (simulation_id, valuation_at),
    CONSTRAINT fk_simulation_equity_run FOREIGN KEY (simulation_id)
        REFERENCES trading.simulation_run (simulation_id) ON DELETE CASCADE,
    CONSTRAINT ck_simulation_equity_total_asset_value CHECK (total_asset_value >= 0),
    CONSTRAINT ck_simulation_equity_cash_balance CHECK (cash_balance >= 0)
);

CREATE TABLE trading.simulation_result (
    simulation_id       UUID          NOT NULL,
    as_of               TIMESTAMPTZ   NOT NULL,
    asset_value         NUMERIC(24,8) NOT NULL,
    cumulative_return   NUMERIC(14,8) NOT NULL,
    mdd                 NUMERIC(14,8) NULL,
    sharpe_ratio        NUMERIC(14,8) NULL,
    volatility          NUMERIC(14,8) NULL,
    win_rate            NUMERIC(14,8) NULL,
    trade_count         INTEGER       NOT NULL,
    total_fee           NUMERIC(24,8) NOT NULL,

    CONSTRAINT pk_simulation_result PRIMARY KEY (simulation_id),
    CONSTRAINT fk_simulation_result_run FOREIGN KEY (simulation_id)
        REFERENCES trading.simulation_run (simulation_id) ON DELETE CASCADE,
    CONSTRAINT ck_simulation_result_asset_value CHECK (asset_value >= 0),
    CONSTRAINT ck_simulation_result_trade_count CHECK (trade_count >= 0),
    CONSTRAINT ck_simulation_result_total_fee CHECK (total_fee >= 0)
);
