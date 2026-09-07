CREATE TABLE trading.backtest_result (
     backtest_id          UUID          NOT NULL,
     final_asset_value    NUMERIC(24,8) NOT NULL,
     cumulative_return    NUMERIC(14,8) NOT NULL,
     cagr                 NUMERIC(14,8) NOT NULL,
     mdd                  NUMERIC(14,8) NOT NULL,
     sharpe_ratio         NUMERIC(14,8) NULL,
     volatility           NUMERIC(14,8) NULL,
     win_rate             NUMERIC(14,8) NULL,
     trade_count          INTEGER       NOT NULL,
     total_fee            NUMERIC(24,8) NOT NULL,
     benchmark_return     NUMERIC(14,8) NULL,

     CONSTRAINT pk_backtest_result
         PRIMARY KEY (backtest_id),

     CONSTRAINT fk_backtest_result_run
         FOREIGN KEY (backtest_id)
             REFERENCES trading.backtest_run (backtest_id)
             ON DELETE CASCADE,

     CONSTRAINT ck_backtest_result_final_asset_value
         CHECK (final_asset_value >= 0),

     CONSTRAINT ck_backtest_result_trade_count
         CHECK (trade_count >= 0),

     CONSTRAINT ck_backtest_result_total_fee
         CHECK (total_fee >= 0)
);