package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.AssetTradingStatus;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.repository.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AssetReadServiceTest {
    @Mock
    private AssetRepository repository;

    @Test
    @DisplayName("Upbit 원화 시장의 거래지원 종목만 조회한다")
    void readTradingWonAssets() {
        given(repository.findByExchangeAndQuoteCurrencyCodeAndTradingStatusOrderByExchangeMarketCodeAsc(
                Exchange.UPBIT, "KRW", AssetTradingStatus.TRADING)).willReturn(List.of());
        assertThat(new AssetReadService(repository).findChartAssets()).isEmpty();
        then(repository).should().findByExchangeAndQuoteCurrencyCodeAndTradingStatusOrderByExchangeMarketCodeAsc(
                Exchange.UPBIT, "KRW", AssetTradingStatus.TRADING);
    }
}
