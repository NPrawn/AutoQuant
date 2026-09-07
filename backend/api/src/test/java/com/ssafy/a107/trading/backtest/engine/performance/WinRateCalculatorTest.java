package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class WinRateCalculatorTest {

    private final WinRateCalculator calculator = new WinRateCalculator();

    @DisplayName("승리 거래 수를 전체 매도 거래 수로 나누어 승률을 계산한다")
    @Test
    void calculateWinRate() {
        Optional<BigDecimal> winRate = calculator.calculate(List.of(
                new BigDecimal("10000"),
                new BigDecimal("-5000"),
                BigDecimal.ZERO
        ));

        assertThat(winRate).isPresent();
        assertThat(winRate.orElseThrow())
                .isEqualByComparingTo("0.33333333");
    }

    @DisplayName("모든 거래가 수익이면 승률은 1이다")
    @Test
    void returnOneWhenAllTradesWin() {
        Optional<BigDecimal> winRate = calculator.calculate(List.of(
                BigDecimal.ONE,
                BigDecimal.TEN
        ));

        assertThat(winRate).isPresent();
        assertThat(winRate.orElseThrow())
                .isEqualByComparingTo("1.00000000");
    }

    @DisplayName("수익 거래가 없으면 승률은 0이다")
    @Test
    void returnZeroWhenNoTradeWins() {
        Optional<BigDecimal> winRate = calculator.calculate(List.of(
                BigDecimal.ONE.negate(),
                BigDecimal.ZERO
        ));

        assertThat(winRate).isPresent();
        assertThat(winRate.orElseThrow())
                .isEqualByComparingTo("0.00000000");
    }

    @DisplayName("거래가 없으면 승률을 반환하지 않는다")
    @Test
    void returnEmptyWhenNoTradeExists() {
        Optional<BigDecimal> winRate = calculator.calculate(List.of());

        assertThat(winRate).isEmpty();
    }

    @DisplayName("실현손익 목록에 null이 포함될 수 없다")
    @Test
    void rejectNullProfitAndLoss() {
        List<BigDecimal> realizedProfitLosses = new ArrayList<>();
        realizedProfitLosses.add(BigDecimal.ONE);
        realizedProfitLosses.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        realizedProfitLosses
                ));
    }

    @DisplayName("실현손익 목록은 null일 수 없다")
    @Test
    void rejectNullProfitAndLossList() {
        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(null));
    }
}
