package com.ssafy.a107.contest.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.contest.controller.request.SubmitContestEntryRequest;
import com.ssafy.a107.contest.controller.response.ContestEntryResponse;
import com.ssafy.a107.contest.controller.response.ContestRoundDetailResponse;
import com.ssafy.a107.contest.controller.response.ContestRoundListResponse;
import com.ssafy.a107.contest.service.ContestEntryCommandService;
import com.ssafy.a107.contest.service.ContestReadService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/contests")
public class ContestController {

    private final ContestReadService contestReadService;
    private final ContestEntryCommandService contestEntryCommandService;

    public ContestController(
            ContestReadService contestReadService,
            ContestEntryCommandService contestEntryCommandService
    ) {
        this.contestReadService = contestReadService;
        this.contestEntryCommandService = contestEntryCommandService;
    }

    @GetMapping
    public ContestRoundListResponse getContests(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ContestRoundListResponse.from(contestReadService.getRounds(principal.memberId()));
    }

    @GetMapping("/{roundId}")
    public ContestRoundDetailResponse getContest(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID roundId
    ) {
        return ContestRoundDetailResponse.from(contestReadService.getRound(principal.memberId(), roundId));
    }

    @PutMapping("/{roundId}/entry")
    public ContestEntryResponse submitEntry(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID roundId,
            @Valid @RequestBody SubmitContestEntryRequest request
    ) {
        return ContestEntryResponse.from(
                contestEntryCommandService.submit(principal.memberId(), roundId, request.strategyId())
        );
    }
}
