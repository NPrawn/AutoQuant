package com.ssafy.a107.contest.domain;

/*
 * 실격 사유다. 점수를 낼 수 없는 경우(백테스트 실패)와 현금만 들고 시장과 같은 점수를 받는 경우(투자 비중 미달)만
 * 실격이다. 크게 잃거나 깊이 빠진 전략은 실격 대신 시장 대비 점수가 0점으로 내려간다 (대회 기능 설계 7절).
 */
public enum ContestDisqualificationReason {

    BACKTEST_FAILED,
    LOW_EXPOSURE
}
