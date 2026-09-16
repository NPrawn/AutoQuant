package com.ssafy.a107.contest.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/*
 * 대회 데이터 한 벌이다. 생성기가 만든 결과를 그대로 담아 마감 후 공개한다 (ADR-0010, ADR-0014).
 * source_weeks 는 생성기 weeks.json 의 주 목록을 JSON 문자열 그대로 둔다.
 */
@Entity
@Table(name = "market_dataset", schema = "contest")
public class MarketDataset extends BaseEntity {

    private static final int MAX_GENERATOR_VERSION_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dataset_id", nullable = false, updatable = false)
    private UUID datasetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "round_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_market_dataset_round")
    )
    private ContestRound round;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    private MarketDatasetKind kind;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @Column(name = "evaluation_return", nullable = false, precision = 12, scale = 8, updatable = false)
    private BigDecimal evaluationReturn;

    @Column(name = "source_window_start", nullable = false, updatable = false)
    private Instant sourceWindowStart;

    @Column(name = "source_window_end", nullable = false, updatable = false)
    private Instant sourceWindowEnd;

    @Column(name = "seed", nullable = false, updatable = false)
    private long seed;

    @Column(name = "source_weeks", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String sourceWeeks;

    @Column(name = "generator_version", nullable = false, length = MAX_GENERATOR_VERSION_LENGTH, updatable = false)
    private String generatorVersion;

    @Column(name = "filled_hours", nullable = false, updatable = false)
    private int filledHours;

    protected MarketDataset() {
    }

    public MarketDataset(
            ContestRound round,
            MarketDatasetKind kind,
            Instant periodStart,
            Instant periodEnd,
            BigDecimal evaluationReturn,
            Instant sourceWindowStart,
            Instant sourceWindowEnd,
            long seed,
            String sourceWeeks,
            String generatorVersion,
            int filledHours
    ) {
        this.round = Objects.requireNonNull(round);
        this.kind = Objects.requireNonNull(kind);
        this.periodStart = Objects.requireNonNull(periodStart);
        this.periodEnd = requireAfter(periodStart, periodEnd, "Period end");
        this.evaluationReturn = Objects.requireNonNull(evaluationReturn);
        this.sourceWindowStart = Objects.requireNonNull(sourceWindowStart);
        this.sourceWindowEnd = requireAfter(sourceWindowStart, sourceWindowEnd, "Source window end");
        this.seed = seed;
        this.sourceWeeks = requireNonBlank(sourceWeeks, Integer.MAX_VALUE, "Source weeks");
        this.generatorVersion = requireNonBlank(generatorVersion, MAX_GENERATOR_VERSION_LENGTH, "Generator version");
        this.filledHours = requireNonNegative(filledHours);
    }

    private static Instant requireAfter(Instant start, Instant end, String fieldName) {
        if (end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException(fieldName + " must be after its start");
        }
        return end;
    }

    private static String requireNonBlank(String value, int maximumLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maximumLength + " characters");
        }
        return normalizedValue;
    }

    private static int requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Filled hours must not be negative");
        }
        return value;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public ContestRound getRound() {
        return round;
    }

    public MarketDatasetKind getKind() {
        return kind;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getEvaluationReturn() {
        return evaluationReturn;
    }

    public Instant getSourceWindowStart() {
        return sourceWindowStart;
    }

    public Instant getSourceWindowEnd() {
        return sourceWindowEnd;
    }

    public long getSeed() {
        return seed;
    }

    public String getSourceWeeks() {
        return sourceWeeks;
    }

    public String getGeneratorVersion() {
        return generatorVersion;
    }

    public int getFilledHours() {
        return filledHours;
    }
}
