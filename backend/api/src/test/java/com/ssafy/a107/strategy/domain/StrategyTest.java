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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @DisplayName("새 전략은 뿌리 1판이고 저장된 뒤에는 자기 번호가 뿌리다")
    @Test
    void newStrategyIsFirstVersionOfItsOwnRoot() {
        Strategy strategy = new Strategy(member(), "RSI 전략", null, strategyDsl());

        assertThat(strategy.getVersionNo()).isEqualTo(1);
        assertThat(strategy.getRootStrategyId()).isNull();

        ReflectionTestUtils.setField(strategy, "strategyId", 7L);

        assertThat(strategy.getRootStrategyId()).isEqualTo(7L);
    }

    @DisplayName("수정은 같은 뿌리의 다음 판을 만들고 공개 여부는 물려주지 않는다")
    @Test
    void reviseCreatesNextVersionUnderSameRoot() {
        Strategy root = new Strategy(member(), "RSI 전략", "설명", strategyDsl());
        ReflectionTestUtils.setField(root, "strategyId", 7L);
        root.changeVisibility(true);
        StrategyDsl revisedDsl = strategyDsl();

        Strategy second = root.reviseWith("RSI 전략", "고친 설명", revisedDsl, 2);
        ReflectionTestUtils.setField(second, "strategyId", 9L);
        Strategy third = second.reviseWith("RSI 전략 v3", null, revisedDsl, 3);

        assertThat(second.getRootStrategyId()).isEqualTo(7L);
        assertThat(second.getVersionNo()).isEqualTo(2);
        assertThat(second.getMember()).isSameAs(root.getMember());
        assertThat(second.getDescription()).isEqualTo("고친 설명");
        assertThat(second.getStrategyDsl()).isSameAs(revisedDsl);
        assertThat(second.isPublished()).isFalse();
        assertThat(third.getRootStrategyId()).isEqualTo(7L);
        assertThat(third.getVersionNo()).isEqualTo(3);
        assertThat(third.getName()).isEqualTo("RSI 전략 v3");
    }

    @DisplayName("저장되지 않은 전략이나 1판 이하 번호로는 다음 판을 만들 수 없다")
    @Test
    void rejectRevisionWithoutPersistedRootOrValidVersion() {
        Strategy unsaved = new Strategy(member(), "RSI 전략", null, strategyDsl());
        Strategy saved = new Strategy(member(), "RSI 전략", null, strategyDsl());
        ReflectionTestUtils.setField(saved, "strategyId", 7L);

        assertThatThrownBy(() -> unsaved.reviseWith("RSI 전략", null, strategyDsl(), 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> saved.reviseWith("RSI 전략", null, strategyDsl(), 1))
                .isInstanceOf(IllegalArgumentException.class);
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
