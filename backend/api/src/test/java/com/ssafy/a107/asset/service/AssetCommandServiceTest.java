package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.asset.repository.MarketDataCollectionTargetRepository;
import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssetCommandServiceTest {

    private static final Instant LISTED_AT = Instant.parse("2017-09-25T00:00:00Z");

    private static final UUID ASSET_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private MarketDataCollectionTargetRepository collectionTargetRepository;

    @DisplayName("검증된 종목과 활성 수집 대상을 함께 저장한다")
    @Test
    void createAssetWithCollectionTarget() {
        AssetCommandService service = createService();
        UpbitMarketInformation information = bitcoinMarketInformation();
        given(assetRepository.existsByExchangeAndExchangeMarketCode(Exchange.UPBIT, "KRW-BTC"))
                .willReturn(false);
        given(assetRepository.saveAndFlush(any(Asset.class))).willAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            ReflectionTestUtils.setField(asset, "assetId", ASSET_ID);
            ReflectionTestUtils.setField(asset, "createdAt", CREATED_AT);
            return asset;
        });
        given(collectionTargetRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AssetRegistrationResult result = service.createAsset(
                Exchange.UPBIT,
                information,
                "BTC",
                "KRW",
                LISTED_AT
        );

        assertThat(result.assetId()).isEqualTo(ASSET_ID);
        assertThat(result.exchangeMarketCode()).isEqualTo("KRW-BTC");
        assertThat(result.baseCurrencyCode()).isEqualTo("BTC");
        assertThat(result.quoteCurrencyCode()).isEqualTo("KRW");
        assertThat(result.collectionStatus()).isEqualTo(MarketDataCollectionStatus.ENABLED);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        then(assetRepository).should().saveAndFlush(any(Asset.class));
        then(collectionTargetRepository).should().save(any());
    }

    @DisplayName("동시 등록으로 DB 유니크 제약이 충돌해도 중복 종목 오류로 변환한다")
    @Test
    void translateUniqueConstraintViolation() {
        AssetCommandService service = createService();
        given(assetRepository.existsByExchangeAndExchangeMarketCode(Exchange.UPBIT, "KRW-BTC"))
                .willReturn(false);
        given(assetRepository.saveAndFlush(any(Asset.class)))
                .willThrow(new DataIntegrityViolationException("uq_asset_exchange_market_code"));

        assertThatThrownBy(() -> service.createAsset(
                Exchange.UPBIT,
                bitcoinMarketInformation(),
                "BTC",
                "KRW",
                LISTED_AT
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.ASSET_ALREADY_EXISTS)
                );

        then(collectionTargetRepository).shouldHaveNoInteractions();
    }

    @DisplayName("이미 등록된 종목은 다시 저장하지 않는다")
    @Test
    void rejectDuplicatedAsset() {
        AssetCommandService service = createService();
        given(assetRepository.existsByExchangeAndExchangeMarketCode(Exchange.UPBIT, "KRW-BTC"))
                .willReturn(true);

        assertThatThrownBy(() -> service.createAsset(
                Exchange.UPBIT,
                bitcoinMarketInformation(),
                "BTC",
                "KRW",
                LISTED_AT
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.ASSET_ALREADY_EXISTS)
                );

        then(assetRepository).shouldHaveNoMoreInteractions();
        then(collectionTargetRepository).shouldHaveNoInteractions();
    }

    private AssetCommandService createService() {
        return new AssetCommandService(assetRepository, collectionTargetRepository);
    }

    private UpbitMarketInformation bitcoinMarketInformation() {
        return new UpbitMarketInformation("KRW-BTC", "비트코인", "Bitcoin");
    }
}
