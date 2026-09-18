package com.ssafy.a107.contest.domain;

/*
 * 화면이 쓰는 회차 상태다. 예정과 제출 중은 저장하지 않고 시각으로 계산한다 (대회 기능 설계 5절).
 */
public enum ContestDisplayStatus {

    SCHEDULED,
    OPEN,
    SCORING,
    FINISHED
}
