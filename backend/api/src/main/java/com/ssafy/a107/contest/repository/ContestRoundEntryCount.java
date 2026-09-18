package com.ssafy.a107.contest.repository;

import java.util.UUID;

/* 회차별 참가 수. 목록이 회차마다 세면 질의가 회차 수만큼 늘어난다. */
public record ContestRoundEntryCount(UUID roundId, long entryCount) {
}
