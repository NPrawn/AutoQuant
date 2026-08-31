package com.ssafy.a107.member.domain;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @DisplayName("회원은 외부 로그인 식별 정보와 활성 상태로 생성된다")
    @Test
    void createActiveMemberWithLoginIdentity() {
        Member member = new Member(
                LoginProvider.SSAFY,
                "ssafy-member-107",
                "Member@Example.com",
                "가온"
        );

        assertThat(member.getLoginProvider()).isEqualTo(LoginProvider.SSAFY);
        assertThat(member.getLoginProviderMemberId()).isEqualTo("ssafy-member-107");
        assertThat(member.getEmailAddress()).isEqualTo("member@example.com");
        assertThat(member.getDisplayName()).isEqualTo("가온");
        assertThat(member.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @DisplayName("탈퇴한 회원은 프로필을 변경할 수 없다")
    @Test
    void rejectProfileChangeAfterWithdrawal() {
        Member member = new Member(
                LoginProvider.SSAFY,
                "ssafy-member-107",
                "member@example.com",
                "가온"
        );
        member.withdraw(Instant.parse("2026-08-31T09:00:00Z"));

        assertThatThrownBy(() -> member.changeDisplayName("새 이름"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.MEMBER_INVALID_STATUS_TRANSITION)
                );
    }

    @DisplayName("로그인 제공자의 회원 식별자는 비어 있을 수 없다")
    @Test
    void rejectBlankLoginProviderMemberId() {
        assertThatThrownBy(() -> new Member(
                LoginProvider.SSAFY,
                " ",
                "member@example.com",
                "가온"
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.MEMBER_INVALID_LOGIN_IDENTITY)
                );
    }
}
