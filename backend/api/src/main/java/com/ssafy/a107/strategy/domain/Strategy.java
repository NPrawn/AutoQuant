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
import java.util.UUID;

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

    /* 공개하면 남이 조회하고 복제할 수 있다. 되돌릴 수 있는 값이라 updatable 이다. */
    @Column(name = "published", nullable = false)
    private boolean published;

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

    public void changeVisibility(boolean published) {
        this.published = published;
    }

    /*
     * 복제본은 DSL 만 물려받는다. 공개 여부는 물려받지 않는다.
     * 남의 공개 전략을 복제한 순간 내 이름으로 다시 공개되면 뜻하지 않게 퍼진다.
     */
    public Strategy copyFor(Member owner) {
        return new Strategy(owner, copiedName(name), description, strategyDsl);
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isOwnedBy(UUID memberId) {
        return member.getMemberId().equals(memberId);
    }

    /* 이름 상한을 넘으면 앞을 잘라 사본 표시를 남긴다. 사본인지 아닌지가 이름보다 중요하다. */
    private static String copiedName(String name) {
        String suffix = " (사본)";
        int room = MAX_NAME_LENGTH - suffix.length();
        String base = name.length() > room ? name.substring(0, room) : name;
        return base + suffix;
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
