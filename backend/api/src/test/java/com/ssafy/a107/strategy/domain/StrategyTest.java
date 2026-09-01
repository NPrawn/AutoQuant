package com.ssafy.a107.strategy.domain;

import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
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

import static org.assertj.core.api.Assertions.assertThat;

class StrategyTest {

    @DisplayName("전략은 생성 시점의 DSL과 소유자 정보를 보존한다")
    @Test
    void createStrategyWithDslAndMember() {
        Member member = member();
        StrategyDsl strategyDsl = strategyDsl();

        Strategy strategy = new Strategy(member, " RSI 전략 ", "과매도 구간을 매수한다.", strategyDsl);

        assertThat(strategy.getMember()).isSameAs(member);
        assertThat(strategy.getName()).isEqualTo("RSI 전략");
        assertThat(strategy.getDescription()).isEqualTo("과매도 구간을 매수한다.");
        assertThat(strategy.getStrategyDsl()).isSameAs(strategyDsl);
        assertThat(strategy.getDeletedAt()).isNull();
    }

    @DisplayName("전략 삭제는 삭제 시각만 기록하는 soft delete로 처리한다")
    @Test
    void markStrategyAsDeleted() {
        Strategy strategy = new Strategy(member(), "RSI 전략", null, strategyDsl());
        Instant deletedAt = Instant.parse("2026-09-01T00:00:00Z");

        strategy.delete(deletedAt);

        assertThat(strategy.getDeletedAt()).isEqualTo(deletedAt);
    }

    private Member member() {
        return new Member(
                LoginProvider.SSAFY,
                "ssafy-member-107",
                "strategy@example.com",
                "전략 사용자"
        );
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
