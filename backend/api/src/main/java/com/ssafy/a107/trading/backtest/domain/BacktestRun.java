package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "backtest_run", schema = "trading")
public class BacktestRun extends BaseEntity {

    private static final int MAX_QUOTE_CURRENCY_LENGTH = 10;
    private static final int MAX_ENGINE_VERSION_LENGTH = 50;
    private static final int MAX_MARKET_DATA_VERSION_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "backtest_id", nullable = false, updatable = false)
    private UUID backtestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_run_member")
    )
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "strategy_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_backtest_run_strategy")
    )
    private Strategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BacktestRunStatus status;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @Convert(converter = TimeframeAttributeConverter.class)
    @Column(name = "timeframe", nullable = false, length = 10, updatable = false)
    private Timeframe timeframe;

    @Column(name = "quote_currency", nullable = false, length = MAX_QUOTE_CURRENCY_LENGTH, updatable = false)
    private String quoteCurrency;

    @Column(name = "initial_capital", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal initialCapital;

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

    @Column(name = "market_data_version", nullable = false, length = MAX_MARKET_DATA_VERSION_LENGTH, updatable = false)
    private String marketDataVersion;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    protected BacktestRun() {
    }

    public BacktestRun(
            Member member,
            Strategy strategy,
            Instant periodStart,
            Instant periodEnd,
            Timeframe timeframe,
            String quoteCurrency,
            BigDecimal initialCapital,
            BigDecimal feeRate,
            BigDecimal slippageBps,
            RebalancePeriod rebalanceInterval,
            ExecutionPriceRule executionPriceRule,
            String engineVersion,
            String marketDataVersion,
            Instant queuedAt
    ) {
        this.member = Objects.requireNonNull(member);
        this.strategy = Objects.requireNonNull(strategy);
        this.periodStart = Objects.requireNonNull(periodStart);
        this.periodEnd = requirePeriodEnd(periodStart, periodEnd);
        this.timeframe = Objects.requireNonNull(timeframe);
        this.quoteCurrency = requireUpperCaseCode(quoteCurrency, MAX_QUOTE_CURRENCY_LENGTH);
        this.initialCapital = requirePositive(initialCapital, "Initial capital must be greater than zero");
        this.feeRate = requireNonNegative(feeRate, "Fee rate must not be negative");
        this.slippageBps = requireNonNegative(slippageBps, "Slippage bps must not be negative");
        this.rebalanceInterval = Objects.requireNonNull(rebalanceInterval);
        this.executionPriceRule = Objects.requireNonNull(executionPriceRule);
        this.engineVersion = requireNonBlank(engineVersion, MAX_ENGINE_VERSION_LENGTH, "Engine version");
        this.marketDataVersion = requireNonBlank(marketDataVersion, MAX_MARKET_DATA_VERSION_LENGTH, "Market data version");
        this.queuedAt = Objects.requireNonNull(queuedAt);
        this.status = BacktestRunStatus.QUEUED;
    }

    public void markRunning(Instant startedAt) {
        ensureStatus(BacktestRunStatus.QUEUED);
        this.startedAt = requireAtOrAfterQueuedAt(startedAt);
        this.status = BacktestRunStatus.RUNNING;
    }

    public void complete(Instant completedAt) {
        ensureStatus(BacktestRunStatus.RUNNING);
        this.completedAt = requireAtOrAfterStartedAt(completedAt);
        this.status = BacktestRunStatus.COMPLETED;
    }

    public void fail(String failureReason) {
        ensureStatus(BacktestRunStatus.RUNNING);
        this.failureReason = requireNonBlank(failureReason, Integer.MAX_VALUE, "Failure reason");
        this.status = BacktestRunStatus.FAILED;
    }

    private void ensureStatus(BacktestRunStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("Backtest run must be " + expectedStatus);
        }
    }

    private Instant requireAtOrAfterQueuedAt(Instant startedAt) {
        if (startedAt == null || startedAt.isBefore(queuedAt)) {
            throw new IllegalArgumentException("Started at must not be before queued at");
        }
        return startedAt;
    }

    private Instant requireAtOrAfterStartedAt(Instant completedAt) {
        if (completedAt == null || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Completed at must not be before started at");
        }
        return completedAt;
    }

    private static Instant requirePeriodEnd(Instant periodStart, Instant periodEnd) {
        if (periodEnd == null || !periodStart.isBefore(periodEnd)) {
            throw new IllegalArgumentException("Period end must be after period start");
        }
        return periodEnd;
    }

    private static BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireUpperCaseCode(String value, int maximumLength) {
        return requireNonBlank(value, maximumLength, "Quote currency").toUpperCase(Locale.ROOT);
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

    public UUID getBacktestId() {
        return backtestId;
    }

    public Member getMember() {
        return member;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public BacktestRunStatus getStatus() {
        return status;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public BigDecimal getSlippageBps() {
        return slippageBps;
    }

    public RebalancePeriod getRebalanceInterval() {
        return rebalanceInterval;
    }

    public ExecutionPriceRule getExecutionPriceRule() {
        return executionPriceRule;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public String getMarketDataVersion() {
        return marketDataVersion;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
