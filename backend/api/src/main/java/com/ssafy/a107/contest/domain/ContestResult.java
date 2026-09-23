package com.ssafy.a107.contest.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/*
 * 제출 하나의 채점 결과다. 실격이면 점수와 순위가 비고, 실격이 아니면 모두 채워진다 (ADR-0002, ADR-0003).
 */
@Entity
@Table(name = "contest_result", schema = "contest")
public class ContestResult extends BaseEntity {

    @Id
    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "return_score", precision = 6, scale = 3)
    private BigDecimal returnScore;

    @Column(name = "bull_regime_score", precision = 6, scale = 3)
    private BigDecimal bullRegimeScore;

    @Column(name = "bear_regime_score", precision = 6, scale = 3)
    private BigDecimal bearRegimeScore;

    @Column(name = "sideways_regime_score", precision = 6, scale = 3)
    private BigDecimal sidewaysRegimeScore;

    @Column(name = "stability_score", precision = 6, scale = 3)
    private BigDecimal stabilityScore;

    @Column(name = "total_score", precision = 6, scale = 3)
    private BigDecimal totalScore;

    /* 순위표가 읽히게 두는 원본 값이다. 점수를 내는 데는 쓰지 않는다. */
    @Column(name = "general_return", precision = 14, scale = 8)
    private BigDecimal generalReturn;

    @Column(name = "general_mdd", precision = 14, scale = 8)
    private BigDecimal generalMdd;

    @Column(name = "ranking")
    private Integer ranking;

    @Enumerated(EnumType.STRING)
    @Column(name = "disqualification_reason", length = 30)
    private ContestDisqualificationReason disqualificationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "disqualified_kind", length = 10)
    private MarketDatasetKind disqualifiedKind;

    protected ContestResult() {
    }

    public static ContestResult scored(
            UUID entryId,
            BigDecimal returnScore,
            BigDecimal bullRegimeScore,
            BigDecimal bearRegimeScore,
            BigDecimal sidewaysRegimeScore,
            BigDecimal stabilityScore,
            BigDecimal totalScore,
            BigDecimal generalReturn,
            BigDecimal generalMdd,
            int ranking
    ) {
        if (ranking <= 0) {
            throw new IllegalArgumentException("Ranking must be positive");
        }

        ContestResult result = new ContestResult();
        result.entryId = Objects.requireNonNull(entryId);
        result.returnScore = Objects.requireNonNull(returnScore);
        result.bullRegimeScore = Objects.requireNonNull(bullRegimeScore);
        result.bearRegimeScore = Objects.requireNonNull(bearRegimeScore);
        result.sidewaysRegimeScore = Objects.requireNonNull(sidewaysRegimeScore);
        result.stabilityScore = Objects.requireNonNull(stabilityScore);
        result.totalScore = Objects.requireNonNull(totalScore);
        result.generalReturn = Objects.requireNonNull(generalReturn);
        result.generalMdd = Objects.requireNonNull(generalMdd);
        result.ranking = ranking;
        return result;
    }

    public static ContestResult disqualified(
            UUID entryId,
            ContestDisqualificationReason reason,
            MarketDatasetKind kind
    ) {
        ContestResult result = new ContestResult();
        result.entryId = Objects.requireNonNull(entryId);
        result.disqualificationReason = Objects.requireNonNull(reason);
        result.disqualifiedKind = Objects.requireNonNull(kind);
        return result;
    }

    public boolean isDisqualified() {
        return disqualificationReason != null;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public BigDecimal getReturnScore() {
        return returnScore;
    }

    public BigDecimal getBullRegimeScore() {
        return bullRegimeScore;
    }

    public BigDecimal getBearRegimeScore() {
        return bearRegimeScore;
    }

    public BigDecimal getSidewaysRegimeScore() {
        return sidewaysRegimeScore;
    }

    public BigDecimal getStabilityScore() {
        return stabilityScore;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public BigDecimal getGeneralReturn() {
        return generalReturn;
    }

    public BigDecimal getGeneralMdd() {
        return generalMdd;
    }

    public Integer getRanking() {
        return ranking;
    }

    public ContestDisqualificationReason getDisqualificationReason() {
        return disqualificationReason;
    }

    public MarketDatasetKind getDisqualifiedKind() {
        return disqualifiedKind;
    }
}
