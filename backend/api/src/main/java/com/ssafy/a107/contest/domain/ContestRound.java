package com.ssafy.a107.contest.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contest_round", schema = "contest")
public class ContestRound extends BaseEntity {

    private static final int MAX_TITLE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "round_id", nullable = false, updatable = false)
    private UUID roundId;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "submission_starts_at", nullable = false, updatable = false)
    private Instant submissionStartsAt;

    @Column(name = "submission_ends_at", nullable = false, updatable = false)
    private Instant submissionEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private ContestPhase phase;

    @Column(name = "max_submissions", nullable = false, updatable = false)
    private short maxSubmissions;

    @Column(name = "warmup_weeks", nullable = false, updatable = false)
    private short warmupWeeks;

    @Column(name = "evaluation_weeks", nullable = false, updatable = false)
    private short evaluationWeeks;

    @Column(name = "scoring_started_at")
    private Instant scoringStartedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ContestRound() {
    }

    public ContestRound(
            String title,
            Instant submissionStartsAt,
            Instant submissionEndsAt,
            int maxSubmissions,
            int warmupWeeks,
            int evaluationWeeks
    ) {
        this.title = requireTitle(title);
        this.submissionStartsAt = Objects.requireNonNull(submissionStartsAt);
        this.submissionEndsAt = requireSubmissionEndsAt(submissionStartsAt, submissionEndsAt);
        this.maxSubmissions = requirePositiveCount(maxSubmissions, "Max submissions");
        this.warmupWeeks = requirePositiveCount(warmupWeeks, "Warmup weeks");
        this.evaluationWeeks = requirePositiveCount(evaluationWeeks, "Evaluation weeks");
        this.phase = ContestPhase.READY;
    }

    public void startScoring(Instant scoringStartedAt) {
        ensurePhase(ContestPhase.READY);
        if (scoringStartedAt == null || scoringStartedAt.isBefore(submissionEndsAt)) {
            throw new IllegalArgumentException("Scoring must start at or after the submission deadline");
        }
        this.scoringStartedAt = scoringStartedAt;
        this.phase = ContestPhase.SCORING;
    }

    public void finish(Instant finishedAt) {
        ensurePhase(ContestPhase.SCORING);
        if (finishedAt == null || finishedAt.isBefore(scoringStartedAt)) {
            throw new IllegalArgumentException("Finished at must not be before scoring started at");
        }
        this.finishedAt = finishedAt;
        this.phase = ContestPhase.FINISHED;
    }

    /*
     * 예정과 제출 중은 저장하지 않고 시각으로 계산한다. 마감이 지난 READY 회차는 스케줄러가 곧 채점을 시작하므로
     * 화면에는 채점 중으로 보여 준다 (대회 기능 설계 5절).
     */
    public ContestDisplayStatus displayStatus(Instant at) {
        Objects.requireNonNull(at);

        if (phase == ContestPhase.FINISHED) {
            return ContestDisplayStatus.FINISHED;
        }
        if (phase == ContestPhase.SCORING) {
            return ContestDisplayStatus.SCORING;
        }
        if (at.isBefore(submissionStartsAt)) {
            return ContestDisplayStatus.SCHEDULED;
        }
        if (at.isBefore(submissionEndsAt)) {
            return ContestDisplayStatus.OPEN;
        }
        return ContestDisplayStatus.SCORING;
    }

    public boolean isSubmittable(Instant at) {
        Objects.requireNonNull(at);

        return phase == ContestPhase.READY
                && !at.isBefore(submissionStartsAt)
                && at.isBefore(submissionEndsAt);
    }

    private void ensurePhase(ContestPhase expectedPhase) {
        if (phase != expectedPhase) {
            throw new IllegalStateException("Contest round must be " + expectedPhase);
        }
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return normalizedValue;
    }

    private static Instant requireSubmissionEndsAt(Instant startsAt, Instant endsAt) {
        if (endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Submission must end after it starts");
        }
        return endsAt;
    }

    private static short requirePositiveCount(int value, String fieldName) {
        if (value <= 0 || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and " + Short.MAX_VALUE);
        }
        return (short) value;
    }

    public UUID getRoundId() {
        return roundId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getSubmissionStartsAt() {
        return submissionStartsAt;
    }

    public Instant getSubmissionEndsAt() {
        return submissionEndsAt;
    }

    public ContestPhase getPhase() {
        return phase;
    }

    public short getMaxSubmissions() {
        return maxSubmissions;
    }

    public short getWarmupWeeks() {
        return warmupWeeks;
    }

    public short getEvaluationWeeks() {
        return evaluationWeeks;
    }

    public Instant getScoringStartedAt() {
        return scoringStartedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
