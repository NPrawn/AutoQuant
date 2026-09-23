package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestDisqualificationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * 내 제출 한 건. 대회 목록 화면이 "지난 회차" 자리에 이 목록을 놓는다.
 *
 * 제출마다 행이 쌓이므로 같은 회차가 여러 줄 나올 수 있다 (대회 기능 설계 5절). strategyName 은 뿌리
 * 기준 최신 판의 이름이고 versionNo 는 제출한 판이다. 이름을 고치면 새 판이 생기지만 채점된 것은
 * 제출한 판이라, 이름만 따라가게 하고 번호로 어느 판인지 가리킨다.
 *
 * 채점 전에는 totalScore 와 ranking 이 비어 있다. 화면이 채점 중으로 읽는다.
 */
public record ContestMyEntry(
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
}
