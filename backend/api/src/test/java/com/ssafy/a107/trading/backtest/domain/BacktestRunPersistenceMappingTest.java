package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestRunPersistenceMappingTest {

    @DisplayName("BacktestRun은 trading 스키마의 backtest_run 테이블에 매핑한다")
    @Test
    void mapBacktestRunTable() {
        Table table = BacktestRun.class.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("trading");
        assertThat(table.name()).isEqualTo("backtest_run");
    }

    @DisplayName("BacktestRun은 Member와 Strategy를 지연 로딩 다대일 관계로 매핑한다")
    @Test
    void mapMemberAndStrategyAsLazyManyToOne() throws NoSuchFieldException {
        Field memberField = BacktestRun.class.getDeclaredField("member");
        Field strategyField = BacktestRun.class.getDeclaredField("strategy");

        assertManyToOne(memberField, "member_id", "fk_backtest_run_member");
        assertManyToOne(strategyField, "strategy_id", "fk_backtest_run_strategy");
    }

    @DisplayName("BacktestRun의 Timeframe은 현재 DSL의 문자열 값을 저장한다")
    @Test
    void mapTimeframeUsingExistingDslValue() throws NoSuchFieldException {
        Field timeframeField = BacktestRun.class.getDeclaredField("timeframe");
        Convert convert = timeframeField.getAnnotation(Convert.class);
        Column column = timeframeField.getAnnotation(Column.class);

        assertThat(timeframeField.getType()).isEqualTo(Timeframe.class);
        assertThat(convert.converter()).isEqualTo(TimeframeAttributeConverter.class);
        assertThat(column.name()).isEqualTo("timeframe");
        assertThat(column.length()).isEqualTo(10);
        assertThat(column.nullable()).isFalse();
    }

    @DisplayName("BacktestRun의 실행 설정은 V6 마이그레이션 컬럼에 맞게 매핑한다")
    @Test
    void mapExecutionSettings() throws NoSuchFieldException {
        assertColumn("initialCapital", "initial_capital", 24, 8);
        assertColumn("feeRate", "fee_rate", 12, 8);
        assertColumn("slippageBps", "slippage_bps", 12, 4);
        assertColumn("queuedAt", "queued_at", 0, 0);
    }

    private void assertManyToOne(Field field, String columnName, String foreignKeyName) {
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.updatable()).isFalse();
        assertThat(joinColumn.foreignKey().name()).isEqualTo(foreignKeyName);
    }

    private void assertColumn(String fieldName, String columnName, int precision, int scale) throws NoSuchFieldException {
        Field field = BacktestRun.class.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isFalse();
        if (precision > 0) {
            assertThat(column.precision()).isEqualTo(precision);
            assertThat(column.scale()).isEqualTo(scale);
        }
    }
}
