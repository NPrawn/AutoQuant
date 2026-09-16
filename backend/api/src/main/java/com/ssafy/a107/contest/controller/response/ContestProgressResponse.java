package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.service.ContestResultReadService.ContestProgress;

public record ContestProgressResponse(
        ContestDisplayStatus status,
        long finishedBacktests,
        long totalBacktests
) {

    public static ContestProgressResponse from(ContestProgress progress) {
        return new ContestProgressResponse(
                progress.status(),
                progress.finishedBacktests(),
                progress.totalBacktests()
        );
    }
}
