package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.AssetTradingStatus;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.service.port.UpbitMarketClient;
import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssetRegistrationFacadeTest {

    private static final Instant LISTED_AT = Instant.parse("2017-09-25T00:00:00Z");

    @Mock
    private UpbitMarketClient upbitMarketClient;

    @Mock
    private AssetCommandService assetCommandService;

    @DisplayName("Upbit에서 확인한 KRW 종목을 저장 서비스에 전달한다")
    @Test
    void registerSupportedKrwAsset() {
        AssetRegistrationFacade facade = createFacade();
        UpbitMarketInformation marketInformation = new UpbitMarketInformation(
                "KRW-BTC",
                "비트코인",
                "Bitcoin"
        );
        AssetRegistrationResult expectedResult = new AssetRegistrationResult(
                UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe"),
                Exchange.UPBIT,
                "KRW-BTC",
                "BTC",
                "KRW",
                "비트코인",
                "Bitcoin",
                AssetTradingStatus.TRADING,
                MarketDataCollectionStatus.ENABLED,
                Instant.parse("2026-08-31T00:00:00Z")
        );
        given(upbitMarketClient.findMarket("KRW-BTC")).willReturn(Optional.of(marketInformation));
        given(upbitMarketClient.findTradingSupportStartedAt("KRW-BTC")).willReturn(Optional.of(LISTED_AT));
        given(assetCommandService.createAsset(Exchange.UPBIT, marketInformation, "BTC", "KRW", LISTED_AT))
                .willReturn(expectedResult);

        AssetRegistrationResult result = facade.registerAsset(Exchange.UPBIT, "KRW-BTC");

        assertThat(result).isSameAs(expectedResult);
    }

    @DisplayName("KRW가 아닌 마켓은 Upbit 조회 전에 거부한다")
    @Test
    void rejectUnsupportedQuoteCurrency() {
        AssetRegistrationFacade facade = createFacade();

        assertThatThrownBy(() -> facade.registerAsset(Exchange.UPBIT, "BTC-ETH"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_UNSUPPORTED_QUOTE_CURRENCY)
                );

        then(upbitMarketClient).shouldHaveNoInteractions();
        then(assetCommandService).shouldHaveNoInteractions();
    }

    @DisplayName("Upbit에서 찾을 수 없는 종목은 등록하지 않는다")
    @Test
    void rejectUnknownMarket() {
        AssetRegistrationFacade facade = createFacade();
        given(upbitMarketClient.findMarket("KRW-NONE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> facade.registerAsset(Exchange.UPBIT, "KRW-NONE"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_NOT_SUPPORTED_BY_EXCHANGE)
                );

        then(assetCommandService).shouldHaveNoInteractions();
    }

    @DisplayName("구분할 수 없는 마켓 코드 형식은 거부한다")
    @Test
    void rejectInvalidMarketCode() {
        AssetRegistrationFacade facade = createFacade();

        assertThatThrownBy(() -> facade.registerAsset(Exchange.UPBIT, "KRWBTC"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.ASSET_INVALID_MARKET_CODE)
                );
    }

    private AssetRegistrationFacade createFacade() {
        return new AssetRegistrationFacade(upbitMarketClient, assetCommandService);
    }
}
