package com.ssafy.a107.candle.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "minute_candle", schema = "market_data")
public class MinuteCandle extends BaseEntity {

    @EmbeddedId
    private MinuteCandleId id;

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

    @Column(name = "last_event_time", nullable = false)
    private Instant lastEventTime;

    protected MinuteCandle() {
    }

    /*
     * market_data 에 없는 캔들을 엔진에 넘길 때 쓴다. 대회 데이터셋 캔들이 그 경우다 (ADR-0014).
     * 영속화하지 않는 읽기 전용 값이며, 저장은 Go Consumer 만 한다.
     */
    public static MinuteCandle detached(
            MinuteCandleId id,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal tradeVolume,
            BigDecimal tradeAmount,
            Instant lastEventTime
    ) {
        MinuteCandle candle = new MinuteCandle();
        candle.id = Objects.requireNonNull(id);
        candle.openPrice = Objects.requireNonNull(openPrice);
        candle.highPrice = Objects.requireNonNull(highPrice);
        candle.lowPrice = Objects.requireNonNull(lowPrice);
        candle.closePrice = Objects.requireNonNull(closePrice);
        candle.tradeVolume = Objects.requireNonNull(tradeVolume);
        candle.tradeAmount = Objects.requireNonNull(tradeAmount);
        candle.lastEventTime = Objects.requireNonNull(lastEventTime);
        return candle;
    }

    public MinuteCandleId getId() {
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

    public Instant getLastEventTime() {
        return lastEventTime;
    }
}
