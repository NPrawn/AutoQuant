package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestTradePersistenceMappingTest {

    @DisplayName("백테스트 거래 내역을 trading 스키마의 backtest_trade 테이블에 매핑한다")
    @Test
    void mapBacktestTradeTable() {
        Table table = BacktestTrade.class.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("trading");
        assertThat(table.name()).isEqualTo("backtest_trade");
    }

    @DisplayName("거래 식별자는 UUID로 생성한다")
    @Test
    void mapGeneratedTradeId() throws NoSuchFieldException {
        Field tradeId = BacktestTrade.class.getDeclaredField("tradeId");
        GeneratedValue generatedValue = tradeId.getAnnotation(GeneratedValue.class);
        Column column = tradeId.getAnnotation(Column.class);

        assertThat(generatedValue.strategy()).isEqualTo(GenerationType.UUID);
        assertThat(column.name()).isEqualTo("trade_id");
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }

    @DisplayName("백테스트 실행과 자산을 지연 로딩 관계로 매핑한다")
    @Test
    void mapBacktestRunAndAssetRelationships() throws NoSuchFieldException {
        assertManyToOne("backtestRun", "backtest_id", "fk_backtest_trade_run");
        assertManyToOne("asset", "asset_id", "fk_backtest_trade_asset");
    }

    @DisplayName("포지션 방향과 거래 방향을 문자열 enum으로 매핑한다")
    @Test
    void mapTradeEnums() throws NoSuchFieldException {
        assertEnumColumn("positionSide", "position_side", 10);
        assertEnumColumn("side", "side", 4);
    }

    @DisplayName("체결 금액 필드를 NUMERIC 24자리와 소수점 8자리로 매핑한다")
    @Test
    void mapTradeAmounts() throws NoSuchFieldException {
        assertAmountColumn("executedPrice", "executed_price");
        assertAmountColumn("quantity", "quantity");
        assertAmountColumn("notional", "notional");
        assertAmountColumn("feeAmount", "fee_amount");
        assertAmountColumn("slippageAmount", "slippage_amount");
    }

    private void assertManyToOne(
            String fieldName,
            String columnName,
            String foreignKeyName
    ) throws NoSuchFieldException {
        Field field = BacktestTrade.class.getDeclaredField(fieldName);
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
        assertThat(joinColumn.foreignKey().name()).isEqualTo(foreignKeyName);
    }

    private void assertEnumColumn(
            String fieldName,
            String columnName,
            int length
    ) throws NoSuchFieldException {
        Field field = BacktestTrade.class.getDeclaredField(fieldName);
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        Column column = field.getAnnotation(Column.class);

        assertThat(enumerated.value()).isEqualTo(EnumType.STRING);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }

    private void assertAmountColumn(
            String fieldName,
            String columnName
    ) throws NoSuchFieldException {
        Field field = BacktestTrade.class.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.precision()).isEqualTo(24);
        assertThat(column.scale()).isEqualTo(8);
        assertThat(column.nullable()).isFalse();
        assertThat(column.updatable()).isFalse();
    }
}
