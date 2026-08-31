package com.ssafy.a107.member.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MemberCommandServiceTest {

    private static final String PROVIDER_MEMBER_ID = "1234567";
    private static final Instant LOGGED_IN_AT = Instant.parse("2026-08-31T05:00:00Z");

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberCommandService memberCommandService;

    @DisplayName("처음 로그인한 사용자는 회원으로 가입된다")
    @Test
    void registerOnFirstLogin() {
        given(memberRepository.findByLoginProviderAndLoginProviderMemberId(LoginProvider.SSAFY, PROVIDER_MEMBER_ID))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        Member member = registerOrRecordLogin();

        assertThat(member.getLoginProviderMemberId()).isEqualTo(PROVIDER_MEMBER_ID);
        assertThat(member.getEmailAddress()).isEqualTo("gaon@ssafy.com");
        assertThat(member.getDisplayName()).isEqualTo("가온");
        assertThat(member.getLastLoginAt()).isEqualTo(LOGGED_IN_AT);
    }

    @DisplayName("이미 가입한 사용자는 다시 가입되지 않고 로그인 시각만 갱신된다")
    @Test
    void recordLoginForExistingMember() {
        Member existing = activeMember();
        given(memberRepository.findByLoginProviderAndLoginProviderMemberId(LoginProvider.SSAFY, PROVIDER_MEMBER_ID))
                .willReturn(Optional.of(existing));

        Member member = registerOrRecordLogin();

        assertThat(member).isSameAs(existing);
        assertThat(member.getLastLoginAt()).isEqualTo(LOGGED_IN_AT);
        then(memberRepository).should().findByLoginProviderAndLoginProviderMemberId(any(), any());
        then(memberRepository).shouldHaveNoMoreInteractions();
    }

    @DisplayName("첫 로그인이 동시에 들어와 유니크 제약에 걸리면 이미 만들어진 회원을 사용한다")
    @Test
    void reuseMemberCreatedByConcurrentFirstLogin() {
        Member createdByOtherRequest = activeMember();
        given(memberRepository.findByLoginProviderAndLoginProviderMemberId(LoginProvider.SSAFY, PROVIDER_MEMBER_ID))
                .willReturn(Optional.empty(), Optional.of(createdByOtherRequest));
        given(memberRepository.save(any(Member.class)))
                .willThrow(new DataIntegrityViolationException("uq_member_login_provider_identity"));

        Member member = registerOrRecordLogin();

        assertThat(member).isSameAs(createdByOtherRequest);
    }

    @DisplayName("정지된 회원은 로그인할 수 없다")
    @Test
    void rejectSuspendedMember() {
        Member suspended = activeMember();
        suspended.suspend();
        given(memberRepository.findByLoginProviderAndLoginProviderMemberId(LoginProvider.SSAFY, PROVIDER_MEMBER_ID))
                .willReturn(Optional.of(suspended));

        assertThatThrownBy(this::registerOrRecordLogin)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_MEMBER_NOT_ACTIVE));
    }

    @DisplayName("탈퇴한 회원은 로그인할 수 없다")
    @Test
    void rejectWithdrawnMember() {
        Member withdrawn = activeMember();
        withdrawn.withdraw(Instant.parse("2026-08-01T00:00:00Z"));
        given(memberRepository.findByLoginProviderAndLoginProviderMemberId(LoginProvider.SSAFY, PROVIDER_MEMBER_ID))
                .willReturn(Optional.of(withdrawn));

        assertThatThrownBy(this::registerOrRecordLogin)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_MEMBER_NOT_ACTIVE));
    }

    private Member registerOrRecordLogin() {
        return memberCommandService.registerOrRecordLogin(
                LoginProvider.SSAFY,
                PROVIDER_MEMBER_ID,
                "gaon@ssafy.com",
                "가온",
                LOGGED_IN_AT
        );
    }

    private static Member activeMember() {
        return new Member(LoginProvider.SSAFY, PROVIDER_MEMBER_ID, "gaon@ssafy.com", "가온");
    }
}
