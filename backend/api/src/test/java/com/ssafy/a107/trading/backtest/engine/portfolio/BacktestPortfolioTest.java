package com.ssafy.a107.trading.backtest.engine.portfolio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BacktestPortfolioTest {

    @DisplayName("초기 현금으로 빈 백테스트 포트폴리오를 생성한다")
    @Test
    void createEmptyPortfolio() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1000000");
        assertThat(portfolio.getPositions()).isEmpty();
    }

    @DisplayName("보유하지 않은 자산의 포지션은 조회되지 않는다")
    @Test
    void returnEmptyForMissingPosition() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThat(portfolio.findPosition(UUID.randomUUID())).isEmpty();
    }

    @DisplayName("초기 현금은 0보다 커야 한다")
    @Test
    void rejectZeroInitialCash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestPortfolio(BigDecimal.ZERO));
    }
}
