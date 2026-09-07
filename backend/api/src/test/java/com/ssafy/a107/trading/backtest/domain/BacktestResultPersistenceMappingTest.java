package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestResultPersistenceMappingTest {

    @DisplayName("백테스트 최종 성과를 trading 스키마의 backtest_result 테이블에 매핑한다")
    @Test
    void mapBacktestResultTable() {
        Table table = BacktestResult.class.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("trading");
        assertThat(table.name()).isEqualTo("backtest_result");
    }

    @DisplayName("백테스트 실행 식별자를 공유 기본키로 매핑한다")
    @Test
    void mapSharedBacktestRunId() throws NoSuchFieldException {
        Field idField = BacktestResult.class.getDeclaredField("backtestId");
        Column idColumn = idField.getAnnotation(Column.class);
        Field runField = BacktestResult.class.getDeclaredField("backtestRun");
        OneToOne oneToOne = runField.getAnnotation(OneToOne.class);
        JoinColumn joinColumn = runField.getAnnotation(JoinColumn.class);

        assertThat(idField.getAnnotation(Id.class)).isNotNull();
        assertThat(idColumn.name()).isEqualTo("backtest_id");
        assertThat(idColumn.nullable()).isFalse();
        assertThat(idColumn.updatable()).isFalse();
        assertThat(runField.getAnnotation(MapsId.class)).isNotNull();
        assertThat(oneToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(oneToOne.optional()).isFalse();
        assertThat(joinColumn.name()).isEqualTo("backtest_id");
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
        assertThat(joinColumn.foreignKey().name()).isEqualTo("fk_backtest_result_run");
    }

    @DisplayName("최종 자산과 총수수료를 NUMERIC 24자리와 소수점 8자리로 매핑한다")
    @Test
    void mapAmountColumns() throws NoSuchFieldException {
        assertDecimalColumn("finalAssetValue", "final_asset_value", 24, 8, false);
        assertDecimalColumn("totalFee", "total_fee", 24, 8, false);
    }

    @DisplayName("성과 비율을 NUMERIC 14자리와 소수점 8자리로 매핑한다")
    @Test
    void mapRateColumns() throws NoSuchFieldException {
        assertDecimalColumn("cumulativeReturn", "cumulative_return", 14, 8, false);
        assertDecimalColumn("cagr", "cagr", 14, 8, false);
        assertDecimalColumn("mdd", "mdd", 14, 8, false);
        assertDecimalColumn("sharpeRatio", "sharpe_ratio", 14, 8, true);
        assertDecimalColumn("volatility", "volatility", 14, 8, true);
        assertDecimalColumn("winRate", "win_rate", 14, 8, true);
        assertDecimalColumn("benchmarkReturn", "benchmark_return", 14, 8, true);
    }

    @DisplayName("체결 횟수를 필수 정수 컬럼으로 매핑한다")
    @Test
    void mapTradeCount() throws NoSuchFieldException {
        Field field = BacktestResult.class.getDeclaredField("tradeCount");
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo("trade_count");
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }

    private void assertDecimalColumn(
            String fieldName,
            String columnName,
            int precision,
            int scale,
            boolean nullable
    ) throws NoSuchFieldException {
        Field field = BacktestResult.class.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.precision()).isEqualTo(precision);
        assertThat(column.scale()).isEqualTo(scale);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.updatable()).isFalse();
    }
}
