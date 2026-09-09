package com.ssafy.a107.trading.simulation.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.domain.TimeframeAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "simulation_run", schema = "trading")
public class SimulationRun extends BaseEntity {

    private static final int MAX_ENGINE_VERSION_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "simulation_id", nullable = false, updatable = false)
    private UUID simulationId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_run_member"))
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_simulation_run_strategy"))
    private Strategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SimulationRunStatus status;

    @Convert(converter = TimeframeAttributeConverter.class)
    @Column(name = "timeframe", nullable = false, length = 10, updatable = false)
    private Timeframe timeframe;

    @Column(name = "quote_currency", nullable = false, length = 10, updatable = false)
    private String quoteCurrency;

    @Column(name = "initial_capital", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal initialCapital;

    @Column(name = "cash_balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal cashBalance;

    @Column(name = "fee_rate", nullable = false, precision = 12, scale = 8, updatable = false)
    private BigDecimal feeRate;

    @Column(name = "slippage_bps", nullable = false, precision = 12, scale = 4, updatable = false)
    private BigDecimal slippageBps;

    @Enumerated(EnumType.STRING)
    @Column(name = "rebalance_interval", nullable = false, length = 20, updatable = false)
    private RebalancePeriod rebalanceInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_price_rule", nullable = false, length = 30, updatable = false)
    private ExecutionPriceRule executionPriceRule;

    @Column(name = "engine_version", nullable = false, length = MAX_ENGINE_VERSION_LENGTH, updatable = false)
    private String engineVersion;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "last_candle_at")
    private Instant lastCandleAt;

    @Column(name = "last_rebalance_at")
    private Instant lastRebalanceAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    protected SimulationRun() {
    }

    public SimulationRun(Member member, Strategy strategy, BigDecimal initialCapital,
                         BigDecimal feeRate, BigDecimal slippageBps,
                         ExecutionPriceRule executionPriceRule, String engineVersion) {
        this.member = Objects.requireNonNull(member);
        this.strategy = Objects.requireNonNull(strategy);
        this.timeframe = Objects.requireNonNull(strategy.getStrategyDsl().timeframe());
        this.quoteCurrency = "KRW";
        this.initialCapital = requirePositive(initialCapital, "Initial capital must be greater than zero");
        this.cashBalance = initialCapital;
        this.feeRate = requireFeeRate(feeRate);
        this.slippageBps = requireNonNegative(slippageBps, "Slippage bps must not be negative");
        this.rebalanceInterval = Objects.requireNonNull(strategy.getStrategyDsl().rebalance().period());
        this.executionPriceRule = Objects.requireNonNull(executionPriceRule);
        this.engineVersion = requireNonBlank(engineVersion, "Engine version");
        this.status = SimulationRunStatus.CREATED;
    }

    public void start(Instant startedAt) {
        if (status != SimulationRunStatus.CREATED && status != SimulationRunStatus.STOPPED) {
            throw new IllegalStateException("Simulation run must be created or stopped");
        }
        this.startedAt = Objects.requireNonNull(startedAt);
        this.stoppedAt = null;
        this.failureReason = null;
        this.status = SimulationRunStatus.RUNNING;
    }

    public void stop(Instant stoppedAt) {
        ensureRunning();
        this.stoppedAt = Objects.requireNonNull(stoppedAt);
        this.status = SimulationRunStatus.STOPPED;
    }

    public boolean beginCandle(Instant candleAt) {
        ensureRunning();
        Objects.requireNonNull(candleAt);
        if (lastCandleAt != null && !candleAt.isAfter(lastCandleAt)) {
            return false;
        }
        this.lastCandleAt = candleAt;
        return true;
    }

    public void markRebalanced(Instant rebalanceAt) {
        ensureRunning();
        if (lastCandleAt == null || rebalanceAt == null || rebalanceAt.isAfter(lastCandleAt)) {
            throw new IllegalArgumentException("Rebalance time must not be after the last candle time");
        }
        this.lastRebalanceAt = rebalanceAt;
    }

    public void changeCashBalance(BigDecimal cashBalance) {
        ensureRunning();
        this.cashBalance = requireNonNegative(cashBalance, "Cash balance must not be negative");
    }

    public void fail(String failureReason) {
        if (status != SimulationRunStatus.CREATED && status != SimulationRunStatus.RUNNING) {
            throw new IllegalStateException("Simulation run must be created or running");
        }
        this.failureReason = requireNonBlank(failureReason, "Failure reason");
        this.status = SimulationRunStatus.FAILED;
    }

    private void ensureRunning() {
        if (status != SimulationRunStatus.RUNNING) {
            throw new IllegalStateException("Simulation run must be running");
        }
    }

    private static BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(message);
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
        return value;
    }

    private static BigDecimal requireFeeRate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Fee rate must be between 0 and 1");
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > MAX_ENGINE_VERSION_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be present and at most 50 characters");
        }
        return value.trim();
    }

    public UUID getSimulationId() { return simulationId; }
    public Member getMember() { return member; }
    public Strategy getStrategy() { return strategy; }
    public SimulationRunStatus getStatus() { return status; }
    public Timeframe getTimeframe() { return timeframe; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public BigDecimal getFeeRate() { return feeRate; }
    public BigDecimal getSlippageBps() { return slippageBps; }
    public RebalancePeriod getRebalanceInterval() { return rebalanceInterval; }
    public ExecutionPriceRule getExecutionPriceRule() { return executionPriceRule; }
    public String getEngineVersion() { return engineVersion; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public Instant getLastCandleAt() { return lastCandleAt; }
    public Instant getLastRebalanceAt() { return lastRebalanceAt; }
    public String getFailureReason() { return failureReason; }
}
