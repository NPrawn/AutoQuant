package com.ssafy.a107.asset.domain;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetTest {

    private static final Instant TRADING_SUPPORT_STARTED_AT = Instant.parse("2017-09-25T00:00:00Z");

    @DisplayName("거래 자산은 거래소 마켓과 기준 통화를 구분하여 생성된다")
    @Test
    void createTradableAssetWithExplicitMarketInformation() {
        Asset asset = createBitcoinAsset();

        assertThat(asset.getExchange()).isEqualTo(Exchange.UPBIT);
        assertThat(asset.getExchangeMarketCode()).isEqualTo("KRW-BTC");
        assertThat(asset.getBaseCurrencyCode()).isEqualTo("BTC");
        assertThat(asset.getQuoteCurrencyCode()).isEqualTo("KRW");
        assertThat(asset.getTradingStatus()).isEqualTo(AssetTradingStatus.TRADING);
    }

    @DisplayName("거래지원 종료 시각은 시작 시각보다 빠를 수 없다")
    @Test
    void rejectTradingEndBeforeTradingStart() {
        Asset asset = createBitcoinAsset();

        assertThatThrownBy(() -> asset.endTradingSupport(Instant.parse("2017-09-24T23:59:59Z")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_INVALID_TRADING_PERIOD)
                );
    }

    @DisplayName("거래 대상 통화와 가격 표시 통화는 서로 달라야 한다")
    @Test
    void rejectSameBaseAndQuoteCurrency() {
        assertThatThrownBy(() -> new Asset(
                Exchange.UPBIT,
                "BTC-BTC",
                "BTC",
                "BTC",
                "비트코인",
                "Bitcoin",
                TRADING_SUPPORT_STARTED_AT
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_INVALID_MARKET_INFORMATION)
                );
    }

    private Asset createBitcoinAsset() {
        return new Asset(
                Exchange.UPBIT,
                "krw-btc",
                "btc",
                "krw",
                "비트코인",
                "Bitcoin",
                TRADING_SUPPORT_STARTED_AT
        );
    }
}
