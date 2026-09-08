ALTER TABLE trading.backtest_trade
    ADD CONSTRAINT ck_backtest_trade_executed_price
        CHECK (executed_price > 0);

ALTER TABLE trading.backtest_equity
    ADD CONSTRAINT ck_backtest_equity_total_asset_value
        CHECK (total_asset_value >= 0),
    ADD CONSTRAINT ck_backtest_equity_cash_balance
        CHECK (cash_balance >= 0),
    ADD CONSTRAINT ck_backtest_equity_cash_within_total_asset
        CHECK (cash_balance <= total_asset_value),
    ADD CONSTRAINT ck_backtest_equity_return_rate
        CHECK (return_rate >= -1),
    ADD CONSTRAINT ck_backtest_equity_drawdown_rate
        CHECK (drawdown_rate >= 0 AND drawdown_rate <= 1);
