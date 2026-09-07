package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestEquityPersistenceMappingTest {

    @DisplayName("백테스트 자산곡선을 trading 스키마의 backtest_equity 테이블에 매핑한다")
    @Test
    void mapBacktestEquityTable() {
        Table table = BacktestEquity.class.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("trading");
        assertThat(table.name()).isEqualTo("backtest_equity");
    }

    @DisplayName("백테스트 ID와 평가 시각을 복합키로 매핑한다")
    @Test
    void mapCompositeId() throws NoSuchFieldException {
        Field idField = BacktestEquity.class.getDeclaredField("id");
        Field backtestIdField = BacktestEquityId.class.getDeclaredField("backtestId");
        Field valuationAtField = BacktestEquityId.class.getDeclaredField("valuationAt");

        assertThat(idField.getAnnotation(EmbeddedId.class)).isNotNull();
        assertColumn(backtestIdField, "backtest_id");
        assertColumn(valuationAtField, "valuation_at");
    }

    @DisplayName("백테스트 실행을 지연 로딩 관계로 매핑한다")
    @Test
    void mapBacktestRunRelationship() throws NoSuchFieldException {
        Field field = BacktestEquity.class.getDeclaredField("backtestRun");
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        MapsId mapsId = field.getAnnotation(MapsId.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(mapsId.value()).isEqualTo("backtestId");
        assertThat(joinColumn.name()).isEqualTo("backtest_id");
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.foreignKey().name()).isEqualTo("fk_backtest_equity_run");
    }

    @DisplayName("자산 금액과 비율 필드를 Flyway 스키마 정밀도에 맞게 매핑한다")
    @Test
    void mapEquityValues() throws NoSuchFieldException {
        assertDecimalColumn("totalAssetValue", "total_asset_value", 24, 8);
        assertDecimalColumn("cashBalance", "cash_balance", 24, 8);
        assertDecimalColumn("returnRate", "return_rate", 14, 8);
        assertDecimalColumn("drawdownRate", "drawdown_rate", 14, 8);
    }

    private void assertColumn(Field field, String columnName) {
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isFalse();
    }

    private void assertDecimalColumn(
            String fieldName,
            String columnName,
            int precision,
            int scale
    ) throws NoSuchFieldException {
        Field field = BacktestEquity.class.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.precision()).isEqualTo(precision);
        assertThat(column.scale()).isEqualTo(scale);
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }
}
