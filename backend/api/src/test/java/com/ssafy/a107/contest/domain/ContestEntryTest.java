package com.ssafy.a107.contest.domain;

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
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ContestEntryTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-17T00:00:00Z");

    @DisplayName("첫 제출은 횟수 1로 시작한다")
    @Test
    void startWithOneSubmission() {
        ContestEntry entry = entry();

        assertThat(entry.getSubmissionCount()).isEqualTo((short) 1);
        assertThat(entry.getFirstSubmittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(entry.getLastSubmittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(entry.remainingSubmissions(5)).isEqualTo(4);
    }

    @DisplayName("교체는 전략과 마지막 제출 시각을 바꾸고 횟수를 늘린다")
    @Test
    void countEveryResubmission() {
        ContestEntry entry = entry();
        Strategy replacement = strategy("교체 전략");
        Instant later = SUBMITTED_AT.plus(Duration.ofHours(1));

        entry.resubmit(replacement, later);

        assertThat(entry.getStrategy()).isSameAs(replacement);
        assertThat(entry.getSubmissionCount()).isEqualTo((short) 2);
        assertThat(entry.getFirstSubmittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(entry.getLastSubmittedAt()).isEqualTo(later);
        assertThat(entry.remainingSubmissions(5)).isEqualTo(3);
    }

    @DisplayName("같은 전략을 다시 제출해도 횟수에 넣는다")
    @Test
    void countResubmissionOfTheSameStrategy() {
        ContestEntry entry = entry();
        Strategy same = entry.getStrategy();

        entry.resubmit(same, SUBMITTED_AT.plusSeconds(1));

        assertThat(entry.getSubmissionCount()).isEqualTo((short) 2);
    }

    @DisplayName("마지막 제출보다 앞선 시각으로는 교체할 수 없다")
    @Test
    void rejectSubmissionBeforeTheLastOne() {
        ContestEntry entry = entry();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> entry.resubmit(strategy("전략"), SUBMITTED_AT.minusSeconds(1)));
    }

    private ContestEntry entry() {
        return new ContestEntry(round(), member(), strategy("첫 전략"), SUBMITTED_AT);
    }

    private ContestRound round() {
        return new ContestRound(
                "1회차",
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-10-16T00:00:00Z"),
                5,
                5,
                13
        );
    }

    private Member member() {
        return new Member(LoginProvider.SSAFY, "contest-member-107", "contest@example.com", "대회 참가자");
    }

    private Strategy strategy(String name) {
        return new Strategy(member(), name, null, strategyDsl());
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
