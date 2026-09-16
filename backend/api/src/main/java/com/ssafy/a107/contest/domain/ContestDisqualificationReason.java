package com.ssafy.a107.contest.domain;

/*
 * 실격 사유다. 데이터셋을 일반장·상승장·하락장·횡보장 순으로 보며 각 데이터셋에서 실패 → 수익률 → 낙폭 순으로
 * 처음 걸린 사유 하나를 남긴다 (대회 기능 설계 7절).
 */
public enum ContestDisqualificationReason {

    BACKTEST_FAILED,
    RETURN_BELOW_LIMIT,
    MDD_ABOVE_LIMIT,
    LOW_EXPOSURE
}
