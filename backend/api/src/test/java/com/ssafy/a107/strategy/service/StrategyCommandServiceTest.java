package com.ssafy.a107.strategy.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StrategyCommandServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Long STRATEGY_ID = 1L;
    private static final Long ROOT_ID = 100L;

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

    @DisplayName("수정 저장은 뿌리의 최대 판 번호 다음 판을 만든다")
    @Test
    void reviseStrategyAsNextVersionOfRoot() {
        StrategyCommandService service = createService();
        Strategy second = strategy(member());
        ReflectionTestUtils.setField(second, "strategyId", STRATEGY_ID);
        ReflectionTestUtils.setField(second, "rootStrategyId", ROOT_ID);
        ReflectionTestUtils.setField(second, "versionNo", 2);
        StrategyDsl revisedDsl = StrategyFixture.strategyDsl();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(second));
        given(strategyRepository.findLatestVersionNo(ROOT_ID)).willReturn(4);
        given(strategyRepository.save(any(Strategy.class))).willAnswer(invocation -> invocation.getArgument(0));

        Strategy revised = service.reviseStrategy(MEMBER_ID, STRATEGY_ID, "RSI 전략", "고침", revisedDsl);

        assertThat(revised.getRootStrategyId()).isEqualTo(ROOT_ID);
        assertThat(revised.getVersionNo()).isEqualTo(5);
        assertThat(revised.getDescription()).isEqualTo("고침");
        assertThat(revised.getStrategyDsl()).isSameAs(revisedDsl);
        assertThat(revised.isPublished()).isFalse();
    }

    @DisplayName("소유하지 않은 전략은 다음 판을 만들 수 없다")
    @Test
    void rejectRevisionOutsideMemberOwnership() {
        StrategyCommandService service = createService();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reviseStrategy(MEMBER_ID, STRATEGY_ID, "RSI 전략", null, StrategyFixture.strategyDsl()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.STRATEGY_NOT_FOUND)
                );

        then(strategyRepository).should(never()).save(any());
    }

    @DisplayName("전략을 삭제하면 같은 뿌리의 모든 판이 함께 삭제된다")
    @Test
    void deleteEveryVersionOfStrategy() {
        StrategyCommandService service = createService();
        Strategy root = strategy(member());
        ReflectionTestUtils.setField(root, "strategyId", ROOT_ID);
        Strategy second = root.reviseWith("RSI 전략", null, StrategyFixture.strategyDsl(), 2);
        ReflectionTestUtils.setField(second, "strategyId", STRATEGY_ID);
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(second));
        given(strategyRepository.findAllActiveByRoot(ROOT_ID, MEMBER_ID)).willReturn(List.of(root, second));

        service.deleteStrategy(MEMBER_ID, STRATEGY_ID);

        assertThat(root.getDeletedAt()).isNotNull();
        assertThat(second.getDeletedAt()).isEqualTo(root.getDeletedAt());
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

    @DisplayName("전략을 공개로 바꾼다")
    @Test
    void publishStrategy() {
        StrategyCommandService service = createService();
        Strategy strategy = strategy(member());
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.of(strategy));

        assertThat(service.changeVisibility(MEMBER_ID, STRATEGY_ID, true).isPublished()).isTrue();
    }

    @DisplayName("소유하지 않은 전략의 공개 여부는 바꿀 수 없다")
    @Test
    void rejectVisibilityChangeOutsideMemberOwnership() {
        StrategyCommandService service = createService();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(STRATEGY_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeVisibility(MEMBER_ID, STRATEGY_ID, true))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.STRATEGY_NOT_FOUND)
                );
    }

    @DisplayName("공개된 남의 전략을 복제하면 내 것이 되고 사본은 비공개로 시작한다")
    @Test
    void copyPublishedStrategyOfOtherMember() {
        StrategyCommandService service = createService();
        Strategy source = strategy(otherMember());
        source.changeVisibility(true);
        Member me = member();
        given(strategyRepository.findByStrategyIdAndDeletedAtIsNull(STRATEGY_ID)).willReturn(Optional.of(source));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(me));
        given(strategyRepository.save(any(Strategy.class))).willAnswer(invocation -> invocation.getArgument(0));

        Strategy copy = service.copyStrategy(MEMBER_ID, STRATEGY_ID);

        assertThat(copy.getMember()).isSameAs(me);
        assertThat(copy.getName()).isEqualTo("RSI 전략 (사본)");
        assertThat(copy.getStrategyDsl()).isSameAs(source.getStrategyDsl());
        assertThat(copy.isPublished()).isFalse();
    }

    @DisplayName("비공개인 남의 전략은 복제할 수 없다")
    @Test
    void rejectCopyOfUnpublishedStrategyOfOtherMember() {
        StrategyCommandService service = createService();
        Strategy source = strategy(otherMember());
        given(strategyRepository.findByStrategyIdAndDeletedAtIsNull(STRATEGY_ID)).willReturn(Optional.of(source));

        assertThatThrownBy(() -> service.copyStrategy(MEMBER_ID, STRATEGY_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.STRATEGY_NOT_FOUND)
                );

        then(strategyRepository).should(never()).save(any());
    }

    @DisplayName("비공개여도 내 전략은 복제할 수 있다")
    @Test
    void copyOwnUnpublishedStrategy() {
        StrategyCommandService service = createService();
        Member me = member();
        Strategy source = strategy(me);
        given(strategyRepository.findByStrategyIdAndDeletedAtIsNull(STRATEGY_ID)).willReturn(Optional.of(source));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(me));
        given(strategyRepository.save(any(Strategy.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.copyStrategy(MEMBER_ID, STRATEGY_ID).getName()).isEqualTo("RSI 전략 (사본)");
    }

    @DisplayName("이름이 상한에 닿아도 사본 표시를 남긴다")
    @Test
    void keepCopyMarkerWithinNameLimit() {
        StrategyCommandService service = createService();
        Member me = member();
        Strategy source = new Strategy(me, "가".repeat(100), null, StrategyFixture.strategyDsl());
        given(strategyRepository.findByStrategyIdAndDeletedAtIsNull(STRATEGY_ID)).willReturn(Optional.of(source));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(me));
        given(strategyRepository.save(any(Strategy.class))).willAnswer(invocation -> invocation.getArgument(0));

        String copiedName = service.copyStrategy(MEMBER_ID, STRATEGY_ID).getName();

        assertThat(copiedName).hasSize(100).endsWith(" (사본)");
    }

    private StrategyCommandService createService() {
        return new StrategyCommandService(strategyRepository, memberRepository);
    }

    private Strategy strategy(Member owner) {
        return new Strategy(owner, "RSI 전략", "설명", StrategyFixture.strategyDsl());
    }

    /* 식별자는 DB 가 채우므로 소유 판단이 필요한 곳에서는 목으로 흉내 낸다. */
    private Member member() {
        Member member = mock(Member.class);
        lenient().when(member.getMemberId()).thenReturn(MEMBER_ID);
        return member;
    }

    private Member otherMember() {
        Member member = mock(Member.class);
        lenient().when(member.getMemberId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        return member;
    }

}
