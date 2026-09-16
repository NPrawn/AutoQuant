package com.ssafy.a107.contest.domain;

/*
 * 랭킹 화면의 정렬 기준(목업 06). 순위는 총점 순으로 매겨져 있어 총점 정렬은 순위 그대로이고,
 * 안정성 정렬은 같은 회차를 다른 눈으로 보는 보조 정렬이다. 순위 값 자체는 바뀌지 않는다.
 */
public enum ContestRankingSort {
    TOTAL,
    STABILITY
}
