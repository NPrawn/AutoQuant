package com.ssafy.a107.contest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
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
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ContestEntryCommandServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID ROUND_ID = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final Long STRATEGY_ID = 1L;

    @Mock
    private ContestRoundRepository contestRoundRepository;

    @Mock
    private ContestEntryRepository contestEntryRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @DisplayName("첫 제출은 제출 횟수 1로 저장한다")
    @Test
    void createEntryOnFirstSubmission() {
        ContestRound round = openRound();
        Strategy strategy = strategy(Timeframe.ONE_HOUR, 20);
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(strategy));
        given(contestEntryRepository.findByRoundRoundIdAndMemberMemberId(ROUND_ID, MEMBER_ID))
                .willReturn(Optional.empty());
        given(contestEntryRepository.save(any(ContestEntry.class))).willAnswer(invocation -> invocation.getArgument(0));

        ContestEntry entry = service().submit(MEMBER_ID, ROUND_ID, STRATEGY_ID);

        assertThat(entry.getSubmissionCount()).isEqualTo((short) 1);
        assertThat(entry.getStrategy()).isSameAs(strategy);
        then(contestEntryRepository).should().save(any(ContestEntry.class));
    }

    @DisplayName("이미 제출했으면 전략을 바꾸고 횟수를 늘린다")
    @Test
    void replaceStrategyOnResubmission() {
        ContestRound round = openRound();
        Strategy replacement = strategy(Timeframe.ONE_HOUR, 20);
        ContestEntry entry = new ContestEntry(round, member(), strategy(Timeframe.ONE_HOUR, 10), Instant.now());
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(replacement));
        given(contestEntryRepository.findByRoundRoundIdAndMemberMemberId(ROUND_ID, MEMBER_ID))
                .willReturn(Optional.of(entry));

        ContestEntry submitted = service().submit(MEMBER_ID, ROUND_ID, STRATEGY_ID);

        assertThat(submitted.getSubmissionCount()).isEqualTo((short) 2);
        assertThat(submitted.getStrategy()).isSameAs(replacement);
        then(contestEntryRepository).should(org.mockito.Mockito.never()).save(any(ContestEntry.class));
    }

    @DisplayName("없는 회차에는 제출할 수 없다")
    @Test
    void rejectMissingRound() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.empty());

        assertError(ApiErrorCode.CONTEST_ROUND_NOT_FOUND);
    }

    @DisplayName("제출 기간이 아니면 거절한다")
    @Test
    void rejectSubmissionOutsideThePeriod() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(closedRound()));

        assertError(ApiErrorCode.CONTEST_SUBMISSION_CLOSED);
    }

    @DisplayName("본인의 삭제되지 않은 전략만 제출할 수 있다")
    @Test
    void rejectStrategyOutsideMemberOwnership() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(openRound()));
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertError(ApiErrorCode.STRATEGY_NOT_FOUND);
    }

    @DisplayName("워밍업보다 긴 팩터 기간은 제출할 수 없다")
    @Test
    void rejectFactorPeriodLongerThanWarmup() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(openRound()));
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(strategy(Timeframe.ONE_DAY, 35)));

        assertError(ApiErrorCode.CONTEST_FACTOR_PERIOD_TOO_LONG);
    }

    @DisplayName("제출 횟수를 모두 쓰면 더 제출할 수 없다")
    @Test
    void rejectSubmissionOverTheLimit() {
        ContestRound round = openRound();
        ContestEntry entry = new ContestEntry(round, member(), strategy(Timeframe.ONE_HOUR, 10), Instant.now());
        for (int count = 1; count < round.getMaxSubmissions(); count++) {
            entry.resubmit(strategy(Timeframe.ONE_HOUR, 10), Instant.now().plus(Duration.ofMinutes(count)));
        }
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(strategy(Timeframe.ONE_HOUR, 20)));
        given(contestEntryRepository.findByRoundRoundIdAndMemberMemberId(ROUND_ID, MEMBER_ID))
                .willReturn(Optional.of(entry));

        assertThat(entry.getSubmissionCount()).isEqualTo(round.getMaxSubmissions());
        assertError(ApiErrorCode.CONTEST_SUBMISSION_LIMIT_EXCEEDED);
    }

    private void assertError(ApiErrorCode expected) {
        assertThatThrownBy(() -> service().submit(MEMBER_ID, ROUND_ID, STRATEGY_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }

    private ContestEntryCommandService service() {
        return new ContestEntryCommandService(
                contestRoundRepository,
                contestEntryRepository,
                strategyRepository,
                new BacktestWarmupCandleCountResolver()
        );
    }

    private ContestRound openRound() {
        return new ContestRound(
                "1회차",
                Instant.now().minus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(1)),
                5,
                5,
                13
        );
    }

    private ContestRound closedRound() {
        return new ContestRound(
                "지난 회차",
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().minus(Duration.ofDays(3)),
                5,
                5,
                13
        );
    }

    private Member member() {
        return new Member(LoginProvider.SSAFY, "contest-member-107", "contest@example.com", "대회 참가자");
    }

    private Strategy strategy(Timeframe timeframe, int rankingPeriod) {
        return new Strategy(member(), "전략", null, new StrategyDsl(
                timeframe,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, rankingPeriod, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        ));
    }
}
