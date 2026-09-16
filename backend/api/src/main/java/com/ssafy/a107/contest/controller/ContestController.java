package com.ssafy.a107.contest.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.contest.controller.request.SubmitContestEntryRequest;
import com.ssafy.a107.contest.controller.response.ContestDatasetsResponse;
import com.ssafy.a107.contest.controller.response.ContestEntryResponse;
import com.ssafy.a107.contest.controller.response.ContestMyResultResponse;
import com.ssafy.a107.contest.controller.response.ContestProgressResponse;
import com.ssafy.a107.contest.controller.response.ContestResultsResponse;
import com.ssafy.a107.contest.controller.response.ContestRoundDetailResponse;
import com.ssafy.a107.contest.controller.response.ContestRoundListResponse;
import com.ssafy.a107.contest.service.ContestEntryCommandService;
import com.ssafy.a107.contest.service.ContestReadService;
import com.ssafy.a107.contest.service.ContestResultReadService;
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
    private final ContestResultReadService contestResultReadService;

    public ContestController(
            ContestReadService contestReadService,
            ContestEntryCommandService contestEntryCommandService,
            ContestResultReadService contestResultReadService
    ) {
        this.contestReadService = contestReadService;
        this.contestEntryCommandService = contestEntryCommandService;
        this.contestResultReadService = contestResultReadService;
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

    @GetMapping("/{roundId}/progress")
    public ContestProgressResponse getProgress(
            @PathVariable UUID roundId
    ) {
        return ContestProgressResponse.from(contestResultReadService.getProgress(roundId));
    }

    @GetMapping("/{roundId}/results")
    public ContestResultsResponse getResults(
            @PathVariable UUID roundId
    ) {
        return ContestResultsResponse.from(contestResultReadService.getResults(roundId));
    }

    @GetMapping("/{roundId}/results/me")
    public ContestMyResultResponse getMyResult(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID roundId
    ) {
        return ContestMyResultResponse.from(contestResultReadService.getMyResult(principal.memberId(), roundId));
    }

    @GetMapping("/{roundId}/datasets")
    public ContestDatasetsResponse getDatasets(
            @PathVariable UUID roundId
    ) {
        return ContestDatasetsResponse.from(contestResultReadService.getDatasets(roundId));
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
