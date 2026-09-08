package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
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
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BacktestRunCommandServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Long STRATEGY_ID = 1L;

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @DisplayName("회원 소유 전략으로 QUEUED 백테스트 실행을 생성한다")
    @Test
    void createQueuedBacktestRunForOwnedStrategy() {
        BacktestRunCommandService service = createService();
        Strategy strategy = strategy();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(strategy));
        given(backtestRunRepository.save(any(BacktestRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BacktestRun backtestRun = service.createBacktestRun(MEMBER_ID, new CreateBacktestRunCommand(
                STRATEGY_ID,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "market-v1"
        ));

        assertThat(backtestRun.getStatus()).isEqualTo(BacktestRunStatus.QUEUED);
        assertThat(backtestRun.getStrategy()).isSameAs(strategy);
        assertThat(backtestRun.getTimeframe()).isEqualTo(strategy.getStrategyDsl().timeframe());
        assertThat(backtestRun.getRebalanceInterval()).isEqualTo(strategy.getStrategyDsl().rebalance().period());
        assertThat(backtestRun.getQueuedAt()).isNotNull();
        then(backtestRunRepository).should().save(any(BacktestRun.class));
    }

    @DisplayName("소유하지 않은 전략으로 백테스트 실행을 생성할 수 없다")
    @Test
    void rejectBacktestRunForStrategyOutsideMemberOwnership() {
        BacktestRunCommandService service = createService();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBacktestRun(MEMBER_ID, new CreateBacktestRunCommand(
                STRATEGY_ID,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "market-v1"
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.STRATEGY_NOT_FOUND)
        );

        then(backtestRunRepository).shouldHaveNoInteractions();
    }

    @DisplayName("기간이 역전된 백테스트 요청은 공통 오류로 거절한다")
    @Test
    void rejectReversedPeriod() {
        BacktestRunCommandService service = createService();

        assertThatThrownBy(() -> service.createBacktestRun(MEMBER_ID, validCommand(
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5)
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_INVALID_PERIOD)
        );

        then(strategyRepository).shouldHaveNoInteractions();
    }

    @DisplayName("지원하지 않는 기준 통화는 공통 오류로 거절한다")
    @Test
    void rejectUnsupportedQuoteCurrency() {
        BacktestRunCommandService service = createService();

        assertThatThrownBy(() -> service.createBacktestRun(MEMBER_ID, validCommand(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                "USD",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5)
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_UNSUPPORTED_QUOTE_CURRENCY)
        );

        then(strategyRepository).shouldHaveNoInteractions();
    }

    @DisplayName("음수 비용은 공통 오류로 거절한다")
    @Test
    void rejectNegativeCost() {
        BacktestRunCommandService service = createService();

        assertThatThrownBy(() -> service.createBacktestRun(MEMBER_ID, validCommand(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(-0.0005),
                BigDecimal.valueOf(5)
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_INVALID_COST)
        );

        then(strategyRepository).shouldHaveNoInteractions();
    }

    @DisplayName("수수료율이 1 이상이면 백테스트 요청을 거절한다")
    @Test
    void rejectFeeRateAtUpperBound() {
        BacktestRunCommandService service = createService();

        assertThatThrownBy(() -> service.createBacktestRun(MEMBER_ID, validCommand(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.ONE,
                BigDecimal.valueOf(5)
        ))).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_INVALID_COST)
        );

        then(strategyRepository).shouldHaveNoInteractions();
    }

    private BacktestRunCommandService createService() {
        return new BacktestRunCommandService(backtestRunRepository, strategyRepository);
    }

    private CreateBacktestRunCommand validCommand(
            Instant periodStart,
            Instant periodEnd,
            String quoteCurrency,
            BigDecimal initialCapital,
            BigDecimal feeRate,
            BigDecimal slippageBps
    ) {
        return new CreateBacktestRunCommand(
                STRATEGY_ID,
                periodStart,
                periodEnd,
                quoteCurrency,
                initialCapital,
                feeRate,
                slippageBps,
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "market-v1"
        );
    }

    private Strategy strategy() {
        Member member = new Member(
                LoginProvider.SSAFY,
                "backtest-member-107",
                "backtest-service@example.com",
                "백테스트 사용자"
        );
        return new Strategy(member, "RSI 전략", null, strategyDsl());
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
