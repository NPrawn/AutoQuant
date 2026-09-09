CREATE TABLE trading.backtest_job_outbox (
    job_id              UUID        NOT NULL,
    backtest_id         UUID        NOT NULL,
    strategy_id         BIGINT      NOT NULL,
    market_data_version VARCHAR(50) NOT NULL,
    engine_version      VARCHAR(50) NOT NULL,
    published_at        TIMESTAMPTZ NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_backtest_job_outbox
        PRIMARY KEY (job_id),
    CONSTRAINT fk_backtest_job_outbox_run
        FOREIGN KEY (backtest_id)
        REFERENCES trading.backtest_run (backtest_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_backtest_job_outbox_pending
    ON trading.backtest_job_outbox (created_at)
    WHERE published_at IS NULL;
