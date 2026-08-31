package com.ssafy.a107.member.controller.response;

import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.domain.MemberStatus;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID memberId,
        String displayName,
        String emailAddress,
        MemberStatus memberStatus,
        Instant lastLoginAt
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getMemberId(),
                member.getDisplayName(),
                member.getEmailAddress(),
                member.getMemberStatus(),
                member.getLastLoginAt()
        );
    }
}
