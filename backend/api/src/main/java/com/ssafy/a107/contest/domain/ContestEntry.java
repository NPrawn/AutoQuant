package com.ssafy.a107.contest.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/*
 * 회차당 회원 한 명의 제출이다. 교체해도 행은 하나고 제출 횟수만 늘어난다 (대회 기능 설계 5절).
 * 전략은 수정 API 가 없어 전략 ID 만 참조해도 제출 시점 내용이 고정된다.
 */
@Entity
@Table(name = "contest_entry", schema = "contest")
public class ContestEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "round_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_contest_entry_round")
    )
    private ContestRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_contest_entry_member")
    )
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "strategy_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_contest_entry_strategy")
    )
    private Strategy strategy;

    @Column(name = "submission_count", nullable = false)
    private short submissionCount;

    @Column(name = "first_submitted_at", nullable = false, updatable = false)
    private Instant firstSubmittedAt;

    @Column(name = "last_submitted_at", nullable = false)
    private Instant lastSubmittedAt;

    protected ContestEntry() {
    }

    public ContestEntry(
            ContestRound round,
            Member member,
            Strategy strategy,
            Instant submittedAt
    ) {
        this.round = Objects.requireNonNull(round);
        this.member = Objects.requireNonNull(member);
        this.strategy = Objects.requireNonNull(strategy);
        this.firstSubmittedAt = Objects.requireNonNull(submittedAt);
        this.lastSubmittedAt = submittedAt;
        this.submissionCount = 1;
    }

    /** 같은 전략을 다시 제출해도 횟수에 넣는다 (대회 기능 설계 5절). */
    public void resubmit(Strategy strategy, Instant submittedAt) {
        Objects.requireNonNull(strategy);
        if (submittedAt == null || submittedAt.isBefore(lastSubmittedAt)) {
            throw new IllegalArgumentException("Submitted at must not be before the last submission");
        }
        if (submissionCount == Short.MAX_VALUE) {
            throw new IllegalStateException("Submission count overflow");
        }
        this.strategy = strategy;
        this.lastSubmittedAt = submittedAt;
        this.submissionCount++;
    }

    public int remainingSubmissions(int maxSubmissions) {
        return Math.max(maxSubmissions - submissionCount, 0);
    }

    public UUID getEntryId() {
        return entryId;
    }

    public ContestRound getRound() {
        return round;
    }

    public Member getMember() {
        return member;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public short getSubmissionCount() {
        return submissionCount;
    }

    public Instant getFirstSubmittedAt() {
        return firstSubmittedAt;
    }

    public Instant getLastSubmittedAt() {
        return lastSubmittedAt;
    }
}
