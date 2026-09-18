package com.ssafy.a107.notification.domain;

/*
 * 알릴 만한 일. 사용자가 화면을 떠난 뒤 끝나거나 조용히 멈추는 것만 둔다.
 * 사용자가 직접 누른 정지처럼 이미 아는 일은 알리지 않는다.
 *
 * targetId 가 가리키는 대상은 종류마다 다르다. 화면이 종류를 보고 어디로 갈지 정한다.
 */
public enum NotificationType {

    /* targetId 는 백테스트 실행 식별자다. */
    BACKTEST_COMPLETED,
    BACKTEST_FAILED,

    /* targetId 는 모의투자 실행 식별자다. 사용자가 멈춘 것이 아니라 엔진이 멈춘 경우다. */
    SIMULATION_FAILED,

    /* targetId 는 대회 회차 식별자다. 제출이 채점되면 백테스트 4벌을 합쳐 한 번만 알린다. */
    CONTEST_SCORED
}
