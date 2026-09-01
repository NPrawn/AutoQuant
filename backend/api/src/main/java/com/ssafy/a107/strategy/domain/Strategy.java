package com.ssafy.a107.strategy.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "strategy", schema = "strategy")
public class Strategy extends BaseEntity {

    private static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "strategy_id", nullable = false, updatable = false)
    private Long strategyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_strategy_member")
    )
    private Member member;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH, updatable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT", updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strategy_dsl", nullable = false, columnDefinition = "jsonb", updatable = false)
    private StrategyDsl strategyDsl;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Strategy() {
    }

    public Strategy(Member member, String name, String description, StrategyDsl strategyDsl) {
        this.member = Objects.requireNonNull(member);
        this.name = requireNonBlankName(name);
        this.description = description;
        this.strategyDsl = Objects.requireNonNull(strategyDsl);
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt);
    }

    private static String requireNonBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Strategy name must not be blank");
        }

        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Strategy name must not exceed 100 characters");
        }
        return normalizedName;
    }

    public Long getStrategyId() {
        return strategyId;
    }

    public Member getMember() {
        return member;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public StrategyDsl getStrategyDsl() {
        return strategyDsl;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
