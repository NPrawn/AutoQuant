package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class BacktestEquityId implements Serializable {

    @Column(name = "backtest_id", nullable = false)
    private UUID backtestId;

    @Column(name = "valuation_at", nullable = false)
    private Instant valuationAt;

    protected BacktestEquityId() {

    }

    public BacktestEquityId(UUID backtestId, Instant valuationAt) {
        this.backtestId = Objects.requireNonNull(backtestId);
        this.valuationAt = Objects.requireNonNull(valuationAt);
    }

    public UUID getBacktestId() {
        return backtestId;
    }

    public Instant getValuationAt() {
        return valuationAt;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof BacktestEquityId that)) {
            return false;
        }
        return Objects.equals(backtestId, that.backtestId)
                && Objects.equals(valuationAt, that.valuationAt);
    }

    @Override
    public int hashCode(){
        return Objects.hash(backtestId, valuationAt);
    }
}
