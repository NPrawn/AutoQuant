package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class BacktestRunTest {

    private static final Instant QUEUED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T00:01:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-01T00:05:00Z");

    @DisplayName("백테스트 실행은 QUEUED 상태와 실행 설정을 보존한다")
    @Test
    void createQueuedBacktestRun() {
        BacktestRun backtestRun = backtestRun();

        assertThat(backtestRun.getStatus()).isEqualTo(BacktestRunStatus.QUEUED);
        assertThat(backtestRun.getMember()).isNotNull();
        assertThat(backtestRun.getStrategy()).isNotNull();
        assertThat(backtestRun.getTimeframe()).isEqualTo(Timeframe.ONE_HOUR);
        assertThat(backtestRun.getQuoteCurrency()).isEqualTo("KRW");
        assertThat(backtestRun.getInitialCapital()).isEqualByComparingTo("1000000");
        assertThat(backtestRun.getQueuedAt()).isEqualTo(QUEUED_AT);
        assertThat(backtestRun.getFailureCode()).isNull();
    }

    @DisplayName("QUEUED 백테스트 실행은 RUNNING으로 전이한다")
    @Test
    void markBacktestRunAsRunning() {
        BacktestRun backtestRun = backtestRun();

        backtestRun.markRunning(STARTED_AT);

        assertThat(backtestRun.getStatus()).isEqualTo(BacktestRunStatus.RUNNING);
        assertThat(backtestRun.getStartedAt()).isEqualTo(STARTED_AT);
    }

    @DisplayName("RUNNING 백테스트 실행은 COMPLETED로 전이한다")
    @Test
    void completeBacktestRun() {
        BacktestRun backtestRun = runningBacktestRun();

        backtestRun.complete(COMPLETED_AT);

        assertThat(backtestRun.getStatus()).isEqualTo(BacktestRunStatus.COMPLETED);
        assertThat(backtestRun.getCompletedAt()).isEqualTo(COMPLETED_AT);
    }

    @DisplayName("RUNNING 백테스트 실행은 실패 원인과 함께 FAILED로 전이한다")
    @Test
    void failBacktestRun() {
        BacktestRun backtestRun = runningBacktestRun();

        backtestRun.fail(
                BacktestFailureCode.INSUFFICIENT_CANDLES,
                "시장 데이터가 부족합니다."
        );

        assertThat(backtestRun.getStatus()).isEqualTo(BacktestRunStatus.FAILED);
        assertThat(backtestRun.getFailureCode()).isEqualTo(BacktestFailureCode.INSUFFICIENT_CANDLES);
        assertThat(backtestRun.getFailureReason()).isEqualTo("시장 데이터가 부족합니다.");
    }

    @DisplayName("완료된 백테스트 실행은 다시 실패 상태로 전이할 수 없다")
    @Test
    void cannotFailCompletedBacktestRun() {
        BacktestRun backtestRun = runningBacktestRun();
        backtestRun.complete(COMPLETED_AT);

        assertThatIllegalStateException()
                .isThrownBy(() -> backtestRun.fail(
                        BacktestFailureCode.ENGINE_ERROR,
                        "실패 원인"
                ));
    }

    @DisplayName("백테스트 기간은 종료 시각이 시작 시각보다 이후여야 한다")
    @Test
    void requireEndAfterStart() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestRun(
                        member(),
                        strategy(),
                        Instant.parse("2026-09-02T00:00:00Z"),
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Timeframe.ONE_HOUR,
                        "KRW",
                        BigDecimal.valueOf(1_000_000),
                        BigDecimal.valueOf(0.0005),
                        BigDecimal.valueOf(5),
                        RebalancePeriod.DAILY,
                        ExecutionPriceRule.NEXT_CANDLE_OPEN,
                        "engine-v1",
                        "market-v1",
                        QUEUED_AT
                ));
    }

    @DisplayName("수수료율은 1보다 작아야 한다")
    @Test
    void rejectFeeRateAtUpperBound() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> backtestRun(BigDecimal.ONE));
    }

    @DisplayName("대회 백테스트만 데이터셋 ID를 가진다")
    @Test
    void createContestBacktestRun() {
        UUID datasetId = UUID.randomUUID();

        BacktestRun contestRun = new BacktestRun(
                member(),
                strategy(),
                Instant.parse("2030-02-11T00:00:00Z"),
                Instant.parse("2030-05-13T00:00:00Z"),
                Timeframe.ONE_HOUR,
                "KRW",
                BigDecimal.valueOf(10_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(10),
                RebalancePeriod.DAILY,
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "contest-round-1",
                QUEUED_AT,
                datasetId
        );

        assertThat(contestRun.getContestDatasetId()).isEqualTo(datasetId);
        assertThat(contestRun.isContest()).isTrue();
        assertThat(backtestRun().isContest()).isFalse();
        assertThat(backtestRun().getContestDatasetId()).isNull();
    }

    private BacktestRun runningBacktestRun() {
        BacktestRun backtestRun = backtestRun();
        backtestRun.markRunning(STARTED_AT);
        return backtestRun;
    }

    private BacktestRun backtestRun() {
        return backtestRun(BigDecimal.valueOf(0.0005));
    }

    private BacktestRun backtestRun(BigDecimal feeRate) {
        return new BacktestRun(
                member(),
                strategy(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                Timeframe.ONE_HOUR,
                "KRW",
                BigDecimal.valueOf(1_000_000),
                feeRate,
                BigDecimal.valueOf(5),
                RebalancePeriod.DAILY,
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "market-v1",
                QUEUED_AT
        );
    }

    private Member member() {
        return new Member(
                LoginProvider.SSAFY,
                "backtest-member-107",
                "backtest@example.com",
                "백테스트 사용자"
        );
    }

    private Strategy strategy() {
        return new Strategy(member(), "RSI 전략", null, strategyDsl());
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }
}
