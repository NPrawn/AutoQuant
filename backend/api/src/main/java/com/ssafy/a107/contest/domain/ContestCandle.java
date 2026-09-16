package com.ssafy.a107.contest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.Objects;

/*
 * 대회 데이터셋의 캔들이다. 값 열은 market_data.minute_candle 과 같고, 로컬 적재 도구가 넣은 뒤 읽기만 한다.
 */
@Entity
@Immutable
@Table(name = "market_candle", schema = "contest")
public class ContestCandle {

    @EmbeddedId
    private ContestCandleId id;

    @Column(name = "open_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal closePrice;

    @Column(name = "trade_volume", nullable = false, precision = 38, scale = 18)
    private BigDecimal tradeVolume;

    @Column(name = "trade_amount", nullable = false, precision = 38, scale = 8)
    private BigDecimal tradeAmount;

    protected ContestCandle() {
    }

    public ContestCandle(
            ContestCandleId id,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal tradeVolume,
            BigDecimal tradeAmount
    ) {
        this.id = Objects.requireNonNull(id);
        this.openPrice = requirePositive(openPrice, "Open price");
        this.highPrice = requirePositive(highPrice, "High price");
        this.lowPrice = requirePositive(lowPrice, "Low price");
        this.closePrice = requirePositive(closePrice, "Close price");
        this.tradeVolume = requireNonNegative(tradeVolume, "Trade volume");
        this.tradeAmount = requireNonNegative(tradeAmount, "Trade amount");
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    public ContestCandleId getId() {
        return id;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public BigDecimal getTradeVolume() {
        return tradeVolume;
    }

    public BigDecimal getTradeAmount() {
        return tradeAmount;
    }
}
