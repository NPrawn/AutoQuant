package com.ssafy.a107.candle.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

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
