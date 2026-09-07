package com.ssafy.a107.trading.backtest.engine.portfolio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RealizedTradeResultTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );

    @DisplayName("순매도대금과 배분 취득원가로 실현이익을 계산한다")
    @Test
    void calculateRealizedProfit() {
        RealizedTradeResult result = new RealizedTradeResult(
                ASSET_ID,
                BigDecimal.ONE,
                new BigDecimal("119940"),
                new BigDecimal("100050")
        );

        assertThat(result.realizedProfitLoss())
                .isEqualByComparingTo("19890.00000000");
    }

    @DisplayName("순매도대금이 배분 취득원가보다 작으면 실현손실을 계산한다")
    @Test
    void calculateRealizedLoss() {
        RealizedTradeResult result = new RealizedTradeResult(
                ASSET_ID,
                BigDecimal.ONE,
                new BigDecimal("89955"),
                new BigDecimal("100050")
        );

        assertThat(result.realizedProfitLoss())
                .isEqualByComparingTo("-10095.00000000");
    }

    @DisplayName("실현 거래 자산 식별자는 null일 수 없다")
    @Test
    void rejectNullAssetId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RealizedTradeResult(
                        null,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE
                ));
    }

    @DisplayName("실현 수량은 0보다 커야 한다")
    @Test
    void rejectNonPositiveQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealizedTradeResult(
                        ASSET_ID,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ONE
                ));
    }

    @DisplayName("순매도대금은 음수일 수 없다")
    @Test
    void rejectNegativeNetProceeds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealizedTradeResult(
                        ASSET_ID,
                        BigDecimal.ONE,
                        BigDecimal.ONE.negate(),
                        BigDecimal.ONE
                ));
    }

    @DisplayName("배분 취득원가는 0보다 커야 한다")
    @Test
    void rejectNonPositiveAllocatedCostBasis() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealizedTradeResult(
                        ASSET_ID,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ));
    }
}
