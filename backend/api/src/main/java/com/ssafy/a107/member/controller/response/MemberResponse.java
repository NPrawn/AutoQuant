package com.ssafy.a107.member.controller.response;

import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID memberId,
        String displayName,
        String emailAddress,
        /* 어느 계정으로 로그인했는지. 계정 메뉴의 정보 블록이 보여 준다. */
        LoginProvider loginProvider,
        MemberStatus memberStatus,
        Instant lastLoginAt
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getMemberId(),
                member.getDisplayName(),
                member.getEmailAddress(),
                member.getLoginProvider(),
                member.getMemberStatus(),
                member.getLastLoginAt()
        );
    }
}
