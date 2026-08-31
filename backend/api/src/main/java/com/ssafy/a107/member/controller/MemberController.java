package com.ssafy.a107.member.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.member.controller.response.MemberResponse;
import com.ssafy.a107.member.service.MemberReadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberReadService memberReadService;

    public MemberController(MemberReadService memberReadService) {
        this.memberReadService = memberReadService;
    }

    /* 인증 주체 전달이 동작하는지 확인하는 첫 보호 엔드포인트. */
    @GetMapping("/me")
    public MemberResponse getMyProfile(@AuthenticationPrincipal MemberPrincipal principal) {
        return MemberResponse.from(memberReadService.getMember(principal.memberId()));
    }
}
