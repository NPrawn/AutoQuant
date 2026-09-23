package com.ssafy.a107.contest.controller.response;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestDisqualificationReason;
import com.ssafy.a107.contest.service.ContestMyEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * 내 대회 제출 목록. 제출마다 한 줄이라 같은 회차가 여러 번 나올 수 있다.
 *
 * strategyName 은 뿌리 기준 최신 판의 이름이고 versionNo 는 제출한 판이다. 채점 전에는 totalScore 와
 * ranking 이 비어 있다.
 */
public record ContestMyEntryListResponse(List<ContestMyEntryItem> entries) {

    public static ContestMyEntryListResponse from(List<ContestMyEntry> entries) {
        return new ContestMyEntryListResponse(entries.stream().map(ContestMyEntryItem::from).toList());
    }

    public record ContestMyEntryItem(
            UUID roundId,
            String roundTitle,
            ContestDisplayStatus roundStatus,
            Long strategyId,
            String strategyName,
            int versionNo,
            Instant submittedAt,
            BigDecimal totalScore,
            Integer ranking,
            ContestDisqualificationReason disqualificationReason
    ) {

        static ContestMyEntryItem from(ContestMyEntry entry) {
            return new ContestMyEntryItem(
                    entry.roundId(),
                    entry.roundTitle(),
                    entry.roundStatus(),
                    entry.strategyId(),
                    entry.strategyName(),
                    entry.versionNo(),
                    entry.submittedAt(),
                    entry.totalScore(),
                    entry.ranking(),
                    entry.disqualificationReason()
            );
        }
    }
}
