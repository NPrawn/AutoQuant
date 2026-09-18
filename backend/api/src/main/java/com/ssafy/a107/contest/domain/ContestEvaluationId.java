package com.ssafy.a107.contest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ContestEvaluationId implements Serializable {

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    protected ContestEvaluationId() {
    }

    public ContestEvaluationId(UUID entryId, UUID datasetId) {
        this.entryId = Objects.requireNonNull(entryId);
        this.datasetId = Objects.requireNonNull(datasetId);
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ContestEvaluationId that)) {
            return false;
        }
        return Objects.equals(entryId, that.entryId) && Objects.equals(datasetId, that.datasetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId, datasetId);
    }
}
