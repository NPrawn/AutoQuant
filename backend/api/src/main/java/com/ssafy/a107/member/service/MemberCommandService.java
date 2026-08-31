package com.ssafy.a107.member.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.domain.MemberStatus;
import com.ssafy.a107.member.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class MemberCommandService {

    private final MemberRepository memberRepository;

    public MemberCommandService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /* 회원을 찾고 없으면 가입시킨 뒤 로그인 시각을 기록한다. 외부 인증 서버 호출은 이 트랜잭션 밖에서 끝나 있어야 한다. */
    public Member registerOrRecordLogin(
            LoginProvider loginProvider,
            String loginProviderMemberId,
            String emailAddress,
            String displayName,
            Instant loggedInAt
    ) {
        Member member = memberRepository
                .findByLoginProviderAndLoginProviderMemberId(loginProvider, loginProviderMemberId)
                .orElseGet(() -> register(loginProvider, loginProviderMemberId, emailAddress, displayName));

        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.AUTH_MEMBER_NOT_ACTIVE);
        }

        member.recordSuccessfulLogin(loggedInAt);
        return member;
    }

    private Member register(
            LoginProvider loginProvider,
            String loginProviderMemberId,
            String emailAddress,
            String displayName
    ) {
        try {
            return memberRepository.save(new Member(loginProvider, loginProviderMemberId, emailAddress, displayName));
        } catch (DataIntegrityViolationException exception) {
            // 같은 사용자의 첫 로그인이 동시에 들어오면 유니크 제약이 한쪽을 막는다. 이미 만들어진 회원을 쓴다.
            return memberRepository
                    .findByLoginProviderAndLoginProviderMemberId(loginProvider, loginProviderMemberId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.MEMBER_NOT_FOUND, exception));
        }
    }
}
