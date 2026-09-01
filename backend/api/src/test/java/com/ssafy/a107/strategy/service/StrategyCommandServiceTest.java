package com.ssafy.a107.strategy.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StrategyCommandServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Long STRATEGY_ID = 1L;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private MemberRepository memberRepository;

    @DisplayName("로그인 회원의 Strategy를 생성한다")
    @Test
    void createStrategyForAuthenticatedMember() {
        StrategyCommandService service = createService();
        Member member = member();
        StrategyDsl strategyDsl = StrategyFixture.strategyDsl();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(strategyRepository.save(any(Strategy.class))).willAnswer(invocation -> invocation.getArgument(0));

        Strategy strategy = service.createStrategy(MEMBER_ID, "RSI 전략", "설명", strategyDsl);

        assertThat(strategy.getMember()).isSameAs(member);
        assertThat(strategy.getName()).isEqualTo("RSI 전략");
        assertThat(strategy.getStrategyDsl()).isSameAs(strategyDsl);
        then(strategyRepository).should().save(any(Strategy.class));
    }

    @DisplayName("존재하지 않는 회원은 Strategy를 생성할 수 없다")
    @Test
    void rejectStrategyCreationForMissingMember() {
        StrategyCommandService service = createService();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createStrategy(MEMBER_ID, "RSI 전략", null, StrategyFixture.strategyDsl()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.MEMBER_NOT_FOUND)
                );

        then(strategyRepository).shouldHaveNoInteractions();
    }

    @DisplayName("소유하지 않았거나 삭제된 Strategy는 삭제할 수 없다")
    @Test
    void rejectDeletionOfStrategyOutsideMemberOwnership() {
        StrategyCommandService service = createService();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteStrategy(MEMBER_ID, STRATEGY_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.STRATEGY_NOT_FOUND)
                );
    }

    private StrategyCommandService createService() {
        return new StrategyCommandService(strategyRepository, memberRepository);
    }

    private Member member() {
        return new Member(LoginProvider.SSAFY, "ssafy-member-107", "strategy@example.com", "전략 사용자");
    }
}
