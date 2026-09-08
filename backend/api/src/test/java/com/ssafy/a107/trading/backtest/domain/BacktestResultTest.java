package com.ssafy.a107.trading.backtest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BacktestResultTest {

    @DisplayName("완료된 백테스트 실행의 최종 성과를 생성한다")
    @Test
    void createBacktestResult() {
        BacktestRun backtestRun = completedBacktestRun();

        BacktestResult result = createResult(
                backtestRun,
                BigDecimal.valueOf(1_100_000),
                4,
                BigDecimal.valueOf(2_000)
        );

        assertThat(result.getBacktestRun()).isSameAs(backtestRun);
        assertThat(result.getFinalAssetValue()).isEqualByComparingTo("1100000");
        assertThat(result.getCumulativeReturn()).isEqualByComparingTo("0.1");
        assertThat(result.getTradeCount()).isEqualTo(4);
        assertThat(result.getTotalFee()).isEqualByComparingTo("2000");
    }

    @DisplayName("완료되지 않은 백테스트 실행에는 최종 성과를 생성할 수 없다")
    @Test
    void rejectNonCompletedBacktestRun() {
        BacktestRun backtestRun = mock(BacktestRun.class);
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.RUNNING);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> createResult(
                        backtestRun,
                        BigDecimal.valueOf(1_100_000),
                        4,
                        BigDecimal.valueOf(2_000)
                ));
    }

    @DisplayName("최종 평가자산은 음수일 수 없다")
    @Test
    void rejectNegativeFinalAssetValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createResult(
                        completedBacktestRun(),
                        BigDecimal.valueOf(-1),
                        4,
                        BigDecimal.valueOf(2_000)
                ));
    }

    @DisplayName("체결 횟수는 음수일 수 없다")
    @Test
    void rejectNegativeTradeCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createResult(
                        completedBacktestRun(),
                        BigDecimal.valueOf(1_100_000),
                        -1,
                        BigDecimal.valueOf(2_000)
                ));
    }

    @DisplayName("총수수료는 음수일 수 없다")
    @Test
    void rejectNegativeTotalFee() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createResult(
                        completedBacktestRun(),
                        BigDecimal.valueOf(1_100_000),
                        4,
                        BigDecimal.valueOf(-1)
                ));
    }

    private BacktestResult createResult(
            BacktestRun backtestRun,
            BigDecimal finalAssetValue,
            int tradeCount,
            BigDecimal totalFee
    ) {
        return new BacktestResult(
                backtestRun,
                finalAssetValue,
                BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(0.12),
                BigDecimal.valueOf(0.08),
                BigDecimal.valueOf(1.2),
                BigDecimal.valueOf(0.15),
                BigDecimal.valueOf(0.5),
                tradeCount,
                totalFee,
                BigDecimal.valueOf(0.05)
        );
    }

    private BacktestRun completedBacktestRun() {
        BacktestRun backtestRun = mock(BacktestRun.class);
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.COMPLETED);
        return backtestRun;
    }
}
