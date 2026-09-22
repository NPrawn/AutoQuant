package com.ssafy.a107.trading.backtest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

class BacktestEquityTest {

    private static final Instant VALUATION_AT = Instant.parse(
            "2026-01-01T00:00:00Z"
    );

    @DisplayName("유효한 백테스트 자산 이력을 생성한다")
    @Test
    void createBacktestEquity() {
        BacktestEquity equity = equity(
                new BigDecimal("1100000"),
                new BigDecimal("500000"),
                new BigDecimal("0.1"),
                new BigDecimal("0.05")
        );

        assertThat(equity.getTotalAssetValue())
                .isEqualByComparingTo("1100000");
        assertThat(equity.getCashBalance())
                .isEqualByComparingTo("500000");
        assertThat(equity.isNew()).isTrue();
    }

    @DisplayName("현금 잔액은 총자산을 초과할 수 없다")
    @Test
    void rejectCashBalanceExceedingTotalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> equity(
                        BigDecimal.ONE,
                        new BigDecimal("2"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("수익률은 -100%보다 작을 수 없다")
    @Test
    void rejectReturnRateBelowNegativeOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> equity(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        new BigDecimal("-1.00000001"),
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("낙폭은 0과 1 사이여야 한다")
    @Test
    void rejectDrawdownRateOutsideRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> equity(
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        new BigDecimal("1.00000001")
                ));
    }

    private BacktestEquity equity(
            BigDecimal totalAssetValue,
            BigDecimal cashBalance,
            BigDecimal returnRate,
            BigDecimal drawdownRate
    ) {
        UUID backtestId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );
        return new BacktestEquity(
                new BacktestEquityId(backtestId, VALUATION_AT),
                mock(BacktestRun.class),
                totalAssetValue,
                cashBalance,
                returnRate,
                drawdownRate
        );
    }
}
