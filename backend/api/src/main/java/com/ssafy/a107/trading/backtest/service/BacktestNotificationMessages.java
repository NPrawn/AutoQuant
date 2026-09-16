package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.trading.backtest.domain.BacktestFailureCode;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/*
 * 알림에 싣는 문구. 실패 사유는 사용자가 다음에 무엇을 할 수 있는지까지 적는다.
 * 내부 예외 이름과 메시지는 넣지 않는다. 화면과 같은 기준으로 읽혀야 한다.
 */
final class BacktestNotificationMessages {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private BacktestNotificationMessages() {
    }

    static String completedTitle(BacktestRun backtestRun) {
        return "%s 백테스트가 끝났습니다".formatted(backtestRun.getStrategy().getName());
    }

    static String completedBody(BacktestRun backtestRun) {
        return "평가 기간 %s ~ %s. 결과를 확인할 수 있습니다.".formatted(
                DATE.format(backtestRun.getPeriodStart()), DATE.format(backtestRun.getPeriodEnd()));
    }

    static String failedTitle(BacktestRun backtestRun) {
        return "%s 백테스트가 실패했습니다".formatted(backtestRun.getStrategy().getName());
    }

    static String failedBody(BacktestFailureCode failureCode) {
        return switch (failureCode) {
            case INSUFFICIENT_CANDLES -> "평가 시작일 앞에 팩터를 계산할 봉이 모자랍니다. "
                    + "시작일을 뒤로 미루거나 기간이 짧은 팩터로 바꾸면 실행할 수 있습니다.";
            case EMPTY_UNIVERSE -> "평가 기간에 조건을 만족하는 종목이 없었습니다. "
                    + "필터를 느슨하게 하거나 기간을 넓히면 실행할 수 있습니다.";
            case ENGINE_ERROR -> "계산 중 문제가 생겨 멈췄습니다. 잠시 후 다시 실행해 주세요.";
        };
    }
}
