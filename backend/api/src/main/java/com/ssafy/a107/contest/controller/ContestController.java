package com.ssafy.a107.contest.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.controller.request.SubmitContestEntryRequest;
import com.ssafy.a107.contest.controller.response.ContestDatasetsResponse;
import com.ssafy.a107.contest.controller.response.ContestEntryResponse;
import com.ssafy.a107.contest.controller.response.ContestMyResultResponse;
import com.ssafy.a107.contest.controller.response.ContestProgressResponse;
import com.ssafy.a107.contest.controller.response.ContestRankingPageResponse;
import com.ssafy.a107.contest.controller.response.ContestResultsResponse;
import com.ssafy.a107.contest.controller.response.ContestRoundDetailResponse;
import com.ssafy.a107.contest.controller.response.ContestRoundListResponse;
import com.ssafy.a107.contest.domain.ContestRankingSort;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.service.ContestEntryCommandService;
import com.ssafy.a107.contest.service.ContestReadService;
import com.ssafy.a107.contest.service.ContestResultReadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/contests")
public class ContestController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

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

    /*
     * 랭킹 화면(목업 06). 정렬은 총점·안정성 두 가지이고, 국면 필터는 그 국면이 가장 강한 제출만 남긴다.
     * 일반장은 세 국면과 견주는 값이 아니라 필터 대상이 아니다.
     */
    @GetMapping("/{roundId}/rankings")
    public ContestRankingPageResponse getRankings(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID roundId,
            @RequestParam(defaultValue = "TOTAL") ContestRankingSort sort,
            @RequestParam(required = false) MarketDatasetKind kind,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || kind == MarketDatasetKind.GENERAL) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        return ContestRankingPageResponse.from(
                contestResultReadService.getRankings(roundId, sort, kind, query, PageRequest.of(page, size)),
                principal.memberId()
        );
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
