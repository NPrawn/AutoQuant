package com.ssafy.a107.strategy.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StrategyReadServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Long STRATEGY_ID = 1L;

    @Mock
    private StrategyRepository strategyRepository;

    @DisplayName("로그인 회원의 활성 Strategy만 목록으로 조회한다")
    @Test
    void getOnlyActiveStrategiesOwnedByMember() {
        StrategyReadService service = new StrategyReadService(strategyRepository);
        Strategy strategy = strategy();
        given(strategyRepository.findAllByMemberMemberIdAndDeletedAtIsNull(MEMBER_ID))
                .willReturn(List.of(strategy));

        List<Strategy> strategies = service.getStrategies(MEMBER_ID);

        assertThat(strategies).containsExactly(strategy);
    }

    @DisplayName("로그인 회원이 소유하지 않은 Strategy는 조회할 수 없다")
    @Test
    void rejectLookupOfStrategyOutsideMemberOwnership() {
        StrategyReadService service = new StrategyReadService(strategyRepository);
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStrategy(MEMBER_ID, STRATEGY_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.STRATEGY_NOT_FOUND)
                );
    }

    private Strategy strategy() {
        Member member = new Member(LoginProvider.SSAFY, "ssafy-member-107", "strategy@example.com", "전략 사용자");
        return new Strategy(member, "RSI 전략", null, StrategyFixture.strategyDsl());
    }
}
