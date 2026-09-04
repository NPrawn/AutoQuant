ALTER TABLE trading.backtest_run
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_backtest_run_version_non_negative
        CHECK (version >= 0),
    ADD CONSTRAINT ck_backtest_run_quote_currency_mvp
        CHECK (quote_currency = 'KRW'),
    ADD CONSTRAINT ck_backtest_run_started_at_state
        CHECK (
            (status = 'QUEUED' AND started_at IS NULL)
            OR (status <> 'QUEUED' AND started_at IS NOT NULL)
        ),
    ADD CONSTRAINT ck_backtest_run_completed_at_state
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL)
        ),
    ADD CONSTRAINT ck_backtest_run_timeline
        CHECK (
            (started_at IS NULL OR started_at >= queued_at)
            AND (completed_at IS NULL OR completed_at >= started_at)
        ),
    ADD CONSTRAINT ck_backtest_run_failure_reason_state
        CHECK (status = 'FAILED' OR failure_reason IS NULL);
