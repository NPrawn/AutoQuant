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
 * 제출 한 건이다. 같은 회원이 여러 번 내면 행이 그만큼 쌓이고, 순위도 제출 단위로 매긴다
 * (대회 기능 설계 5절). 횟수 제한은 없다.
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
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_contest_entry_strategy")
    )
    private Strategy strategy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

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
        this.submittedAt = Objects.requireNonNull(submittedAt);
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

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
