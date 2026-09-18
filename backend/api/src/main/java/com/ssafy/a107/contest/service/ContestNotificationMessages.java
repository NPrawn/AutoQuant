package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestRound;

/*
 * 대회 채점 알림 문구. 제출 한 번에 백테스트 4벌이 돌지만 알림은 제출이 채점될 때 하나만 간다.
 * 점수와 순위는 화면에서 보게 두고 알림에는 적지 않는다. 마감 전 순위는 참가자가 늘면 바뀐다.
 */
final class ContestNotificationMessages {

    private ContestNotificationMessages() {
    }

    static String scoredTitle(ContestRound round) {
        return "%s 채점이 끝났습니다".formatted(round.getTitle());
    }

    static String scoredBody() {
        return "장세 4벌 성적과 점수를 확인할 수 있습니다. 마감 전 순위는 잠정입니다.";
    }
}
