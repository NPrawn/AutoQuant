package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "backtest_job_outbox", schema = "trading")
public class BacktestJobOutbox extends BaseEntity {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "backtest_id", nullable = false, updatable = false)
    private UUID backtestId;

    @Column(name = "strategy_id", nullable = false, updatable = false)
    private Long strategyId;

    @Column(name = "market_data_version", nullable = false, length = 50, updatable = false)
    private String marketDataVersion;

    @Column(name = "engine_version", nullable = false, length = 50, updatable = false)
    private String engineVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected BacktestJobOutbox() {
    }

    public BacktestJobOutbox(BacktestJobMessage message) {
        Objects.requireNonNull(message);
        this.jobId = message.jobId();
        this.backtestId = message.backtestId();
        this.strategyId = message.strategyId();
        this.marketDataVersion = message.marketDataVersion();
        this.engineVersion = message.engineVersion();
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt != null) {
            throw new IllegalStateException("Backtest job outbox is already published");
        }
        this.publishedAt = Objects.requireNonNull(publishedAt);
    }

    public BacktestJobMessage toMessage() {
        return new BacktestJobMessage(
                jobId,
                backtestId,
                strategyId,
                marketDataVersion,
                engineVersion
        );
    }

    public UUID getJobId() {
        return jobId;
    }
}
