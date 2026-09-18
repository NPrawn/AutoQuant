package com.ssafy.a107.contest.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/*
 * 제출 하나를 데이터셋 한 벌에서 돌린 대회 백테스트다. 평균 투자 비중은 채점할 때 equity 에서 구해 적는다
 * (대회 기능 설계 7절).
 */
@Entity
@Table(name = "contest_evaluation", schema = "contest")
public class ContestEvaluation extends BaseEntity {

    @EmbeddedId
    private ContestEvaluationId id;

    @Column(name = "backtest_id", nullable = false)
    private UUID backtestId;

    @Column(name = "average_exposure", precision = 9, scale = 8)
    private BigDecimal averageExposure;

    protected ContestEvaluation() {
    }

    public ContestEvaluation(UUID entryId, UUID datasetId, UUID backtestId) {
        this.id = new ContestEvaluationId(entryId, datasetId);
        this.backtestId = Objects.requireNonNull(backtestId);
    }

    public void recordAverageExposure(BigDecimal averageExposure) {
        if (averageExposure == null
                || averageExposure.signum() < 0
                || averageExposure.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Average exposure must be between 0 and 1");
        }
        this.averageExposure = averageExposure;
    }

    public ContestEvaluationId getId() {
        return id;
    }

    public UUID getBacktestId() {
        return backtestId;
    }

    public BigDecimal getAverageExposure() {
        return averageExposure;
    }
}
