package com.ssafy.a107.asset.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "asset",
        schema = "reference",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_asset_exchange_market_code",
                        columnNames = {"exchange", "exchange_market_code"}
                )
        }
)
public class Asset extends BaseEntity {

    private static final int MAX_MARKET_CODE_LENGTH = 30;
    private static final int MAX_CURRENCY_CODE_LENGTH = 10;
    private static final int MAX_ASSET_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange", nullable = false, length = 20)
    private Exchange exchange;

    @Column(name = "exchange_market_code", nullable = false, length = MAX_MARKET_CODE_LENGTH)
    private String exchangeMarketCode;

    @Column(name = "base_currency_code", nullable = false, length = MAX_CURRENCY_CODE_LENGTH)
    private String baseCurrencyCode;

    @Column(name = "quote_currency_code", nullable = false, length = MAX_CURRENCY_CODE_LENGTH)
    private String quoteCurrencyCode;

    @Column(name = "korean_name", nullable = false, length = MAX_ASSET_NAME_LENGTH)
    private String koreanName;

    @Column(name = "english_name", nullable = false, length = MAX_ASSET_NAME_LENGTH)
    private String englishName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trading_status", nullable = false, length = 20)
    private AssetTradingStatus tradingStatus;

    @Column(name = "trading_support_started_at")
    private Instant tradingSupportStartedAt;

    @Column(name = "trading_support_ended_at")
    private Instant tradingSupportEndedAt;

    protected Asset() {
    }

    public Asset(
            Exchange exchange,
            String exchangeMarketCode,
            String baseCurrencyCode,
            String quoteCurrencyCode,
            String koreanName,
            String englishName,
            Instant tradingSupportStartedAt
    ) {
        if (exchange == null) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_MARKET_INFORMATION);
        }
        this.exchange = exchange;
        this.exchangeMarketCode = normalizeCode(exchangeMarketCode, MAX_MARKET_CODE_LENGTH);
        this.baseCurrencyCode = normalizeCode(baseCurrencyCode, MAX_CURRENCY_CODE_LENGTH);
        this.quoteCurrencyCode = normalizeCode(quoteCurrencyCode, MAX_CURRENCY_CODE_LENGTH);
        if (this.baseCurrencyCode.equals(this.quoteCurrencyCode)) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_MARKET_INFORMATION);
        }
        this.koreanName = requireNonBlank(koreanName, MAX_ASSET_NAME_LENGTH);
        this.englishName = requireNonBlank(englishName, MAX_ASSET_NAME_LENGTH);
        this.tradingStatus = AssetTradingStatus.TRADING;
        this.tradingSupportStartedAt = tradingSupportStartedAt;
    }

    public void changeNames(String koreanName, String englishName) {
        this.koreanName = requireNonBlank(koreanName, MAX_ASSET_NAME_LENGTH);
        this.englishName = requireNonBlank(englishName, MAX_ASSET_NAME_LENGTH);
    }

    public void suspendTrading() {
        if (tradingStatus != AssetTradingStatus.TRADING) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_STATUS_TRANSITION);
        }
        tradingStatus = AssetTradingStatus.SUSPENDED;
    }

    public void resumeTrading() {
        if (tradingStatus != AssetTradingStatus.SUSPENDED) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_STATUS_TRANSITION);
        }
        tradingStatus = AssetTradingStatus.TRADING;
    }

    public void endTradingSupport(Instant endedAt) {
        if (tradingStatus == AssetTradingStatus.DELISTED) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_STATUS_TRANSITION);
        }

        if (endedAt == null) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_TRADING_PERIOD);
        }
        Instant supportEndedAt = endedAt;
        if (tradingSupportStartedAt != null && supportEndedAt.isBefore(tradingSupportStartedAt)) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_TRADING_PERIOD);
        }

        tradingStatus = AssetTradingStatus.DELISTED;
        tradingSupportEndedAt = supportEndedAt;
    }

    private static String normalizeCode(String value, int maximumLength) {
        return requireNonBlank(value, maximumLength).toUpperCase(Locale.ROOT);
    }

    private static String requireNonBlank(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_MARKET_INFORMATION);
        }

        String normalizedValue = value.trim();
        if (normalizedValue.length() > maximumLength) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_MARKET_INFORMATION);
        }
        return normalizedValue;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public String getExchangeMarketCode() {
        return exchangeMarketCode;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }

    public String getQuoteCurrencyCode() {
        return quoteCurrencyCode;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public AssetTradingStatus getTradingStatus() {
        return tradingStatus;
    }

    public Instant getTradingSupportStartedAt() {
        return tradingSupportStartedAt;
    }

    public Instant getTradingSupportEndedAt() {
        return tradingSupportEndedAt;
    }
}
