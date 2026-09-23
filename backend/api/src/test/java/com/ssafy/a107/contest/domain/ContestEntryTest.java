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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ContestEntryTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-17T00:00:00Z");

    @DisplayName("제출은 낸 전략과 시각을 그대로 들고 있는다")
    @Test
    void keepTheSubmittedStrategyAndTime() {
        Strategy submitted = strategy("첫 전략");
        ContestEntry entry = new ContestEntry(round(), member(), submitted, SUBMITTED_AT);

        assertThat(entry.getStrategy()).isSameAs(submitted);
        assertThat(entry.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
    }

    @DisplayName("같은 회원이 다시 내면 별개의 제출이다")
    @Test
    void makeEveryResubmissionItsOwnEntry() {
        Member member = member();
        ContestRound round = round();
        Strategy first = strategy("첫 전략");
        Strategy second = strategy("교체 전략");
        Instant later = SUBMITTED_AT.plus(Duration.ofHours(1));

        ContestEntry earlier = new ContestEntry(round, member, first, SUBMITTED_AT);
        ContestEntry latest = new ContestEntry(round, member, second, later);

        assertThat(earlier.getStrategy()).isSameAs(first);
        assertThat(earlier.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(latest.getStrategy()).isSameAs(second);
        assertThat(latest.getSubmittedAt()).isEqualTo(later);
    }

    @DisplayName("제출 시각이 없으면 만들 수 없다")
    @Test
    void rejectAnEntryWithoutASubmissionTime() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ContestEntry(round(), member(), strategy("전략"), null));
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
