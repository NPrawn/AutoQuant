package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BacktestUniverseAssetId implements Serializable {

    @Column(name = "backtest_id", nullable = false)
    private UUID backtestId;

    @Column(name = "rebalance_at", nullable = false)
    private Instant rebalanceAt;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    protected BacktestUniverseAssetId() {
        
    }

    public BacktestUniverseAssetId(
            UUID backtestId,
            Instant rebalanceAt,
            UUID assetId
    ) {
        this.backtestId = Objects.requireNonNull(backtestId);
        this.rebalanceAt = Objects.requireNonNull(rebalanceAt);
        this.assetId = Objects.requireNonNull(assetId);
    }

    public UUID getBacktestId() {
        return backtestId;
    }

    public Instant getRebalanceAt() {
        return rebalanceAt;
    }

    public UUID getAssetId() {
        return assetId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BacktestUniverseAssetId that)) {
            return false;
        }

        return Objects.equals(backtestId, that.backtestId)
                && Objects.equals(rebalanceAt, that.rebalanceAt)
                && Objects.equals(assetId, that.assetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backtestId, rebalanceAt, assetId);
    }
}
