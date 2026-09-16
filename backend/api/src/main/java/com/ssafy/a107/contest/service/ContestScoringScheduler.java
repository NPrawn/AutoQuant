package com.ssafy.a107.contest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/*
 * 1분마다 세 가지를 한다 (대회 기능 설계 7절).
 * 1. 마감이 지난 회차를 채점 중으로 바꾸고 아직 안 돌린 제출을 돌린다.
 * 2. 백테스트가 끝난 제출의 점수를 저장한다. 마감 전에도 한다.
 * 3. 채점 중 회차의 백테스트가 모두 끝났으면 회차를 종료한다.
 * 백테스트를 실제로 도는 worker 프로필에서는 돌지 않는다.
 */
@Component
@Profile("!backtest-worker")
public class ContestScoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContestScoringScheduler.class);

    private final ContestScoringService contestScoringService;

    public ContestScoringScheduler(ContestScoringService contestScoringService) {
        this.contestScoringService = contestScoringService;
    }

    @Scheduled(fixedDelayString = "${contest.scoring.poll-interval:PT1M}")
    public void scoreDueRounds() {
        Instant now = Instant.now();

        for (UUID roundId : contestScoringService.findRoundsToStart(now)) {
            try {
                contestScoringService.startScoring(roundId, now);
            } catch (RuntimeException exception) {
                log.error("대회 회차 {} 채점 시작 실패", roundId, exception);
            }
        }

        for (UUID roundId : contestScoringService.findRoundsAcceptingScores()) {
            try {
                contestScoringService.scoreSettledEntries(roundId);
            } catch (RuntimeException exception) {
                log.error("대회 회차 {} 중간 채점 실패", roundId, exception);
            }
        }

        for (UUID roundId : contestScoringService.findRoundsInScoring()) {
            try {
                contestScoringService.finishScoring(roundId, Instant.now());
            } catch (RuntimeException exception) {
                log.error("대회 회차 {} 채점 마무리 실패", roundId, exception);
            }
        }
    }
}
