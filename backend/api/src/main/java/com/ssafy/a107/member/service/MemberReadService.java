package com.ssafy.a107.member.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MemberReadService {

    private final MemberRepository memberRepository;

    public MemberReadService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.MEMBER_NOT_FOUND));
    }
}
