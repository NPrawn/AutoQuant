package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContestRoundTest {

    private static final Instant STARTS_AT = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant ENDS_AT = STARTS_AT.plus(Duration.ofDays(7));

    private static ContestRound round() {
        return new ContestRound("1회차", STARTS_AT, ENDS_AT, 5, 13);
    }

    @DisplayName("새 회차는 제출 전 단계로 시작한다")
    @Test
    void startInReadyPhase() {
        ContestRound round = round();

        assertThat(round.getPhase()).isEqualTo(ContestPhase.READY);
        assertThat(round.getScoringStartedAt()).isNull();
    }

    @DisplayName("제출은 제출 기간 안에서만 받는다")
    @Test
    void acceptSubmissionOnlyInsideThePeriod() {
        ContestRound round = round();

        assertThat(round.isSubmittable(STARTS_AT.minusSeconds(1))).isFalse();
        assertThat(round.isSubmittable(STARTS_AT)).isTrue();
        assertThat(round.isSubmittable(ENDS_AT.minusSeconds(1))).isTrue();
        assertThat(round.isSubmittable(ENDS_AT)).isFalse();
    }

    @DisplayName("채점은 마감 뒤에 시작하고 종료는 채점 시작 뒤에 한다")
    @Test
    void moveThroughScoringToFinished() {
        ContestRound round = round();

        assertThatThrownBy(() -> round.startScoring(ENDS_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        round.startScoring(ENDS_AT);
        assertThat(round.getPhase()).isEqualTo(ContestPhase.SCORING);
        assertThat(round.isSubmittable(STARTS_AT.plusSeconds(1))).isFalse();

        round.finish(ENDS_AT.plusSeconds(60));
        assertThat(round.getPhase()).isEqualTo(ContestPhase.FINISHED);
        assertThat(round.getFinishedAt()).isEqualTo(ENDS_AT.plusSeconds(60));
    }

    @DisplayName("제출 기간이 뒤집힌 회차는 만들 수 없다")
    @Test
    void rejectInvertedSubmissionPeriod() {
        assertThatThrownBy(() -> new ContestRound("1회차", ENDS_AT, STARTS_AT, 5, 13))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
