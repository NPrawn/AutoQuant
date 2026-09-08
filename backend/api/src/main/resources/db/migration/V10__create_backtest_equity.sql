CREATE TABLE trading.backtest_equity (
     backtest_id        UUID          NOT NULL,
     valuation_at       TIMESTAMPTZ   NOT NULL,
     total_asset_value  NUMERIC(24,8) NOT NULL,
     cash_balance       NUMERIC(24,8) NOT NULL,
     return_rate        NUMERIC(14,8) NOT NULL,
     drawdown_rate      NUMERIC(14,8) NOT NULL,

     CONSTRAINT pk_backtest_equity
         PRIMARY KEY (backtest_id, valuation_at),

     CONSTRAINT fk_backtest_equity_run
         FOREIGN KEY (backtest_id)
             REFERENCES trading.backtest_run (backtest_id)
             ON DELETE CASCADE
);