package com.ssafy.a107.auth.jwt;

import java.util.UUID;

/* 인증된 요청의 주체. 회원 식별자는 요청 본문이 아니라 이 타입으로만 받는다. */
public record MemberPrincipal(UUID memberId) {
}
