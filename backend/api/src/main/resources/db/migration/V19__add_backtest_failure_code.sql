ALTER TABLE trading.backtest_run
    ADD COLUMN failure_code VARCHAR(30) NULL;

UPDATE trading.backtest_run
SET failure_code = 'ENGINE_ERROR'
WHERE status = 'FAILED';

ALTER TABLE trading.backtest_run
    ADD CONSTRAINT ck_backtest_run_failure_code
        CHECK (
            (status = 'FAILED' AND failure_code IS NOT NULL)
            OR (status <> 'FAILED' AND failure_code IS NULL)
        ),
    ADD CONSTRAINT ck_backtest_run_failure_code_value
        CHECK (
            failure_code IS NULL
            OR failure_code IN ('INSUFFICIENT_CANDLES', 'EMPTY_UNIVERSE', 'ENGINE_ERROR')
        );
