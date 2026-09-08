package com.ssafy.a107.trading.backtest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestValueConstraintMigrationTest {

    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/"
                    + "V12__add_backtest_value_constraints.sql"
    );

    @DisplayName("백테스트 체결과 Equity 값 제약을 DB에 추가한다")
    @Test
    void addBacktestValueConstraints() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "ck_backtest_trade_executed_price",
                "CHECK (executed_price > 0)",
                "ck_backtest_equity_total_asset_value",
                "CHECK (total_asset_value >= 0)",
                "ck_backtest_equity_cash_balance",
                "CHECK (cash_balance >= 0)",
                "ck_backtest_equity_cash_within_total_asset",
                "CHECK (cash_balance <= total_asset_value)",
                "ck_backtest_equity_return_rate",
                "CHECK (return_rate >= -1)",
                "ck_backtest_equity_drawdown_rate",
                "CHECK (drawdown_rate >= 0 AND drawdown_rate <= 1)"
        );
    }
}
