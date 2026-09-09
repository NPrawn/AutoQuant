ALTER TABLE trading.backtest_trade
    DROP CONSTRAINT ck_backtest_trade_signal_filled_time;

ALTER TABLE trading.backtest_trade
    ADD CONSTRAINT ck_backtest_trade_signal_filled_time
        CHECK (signal_at <= filled_at);
