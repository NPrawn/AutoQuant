package com.ssafy.a107.asset.service;

import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.service.port.UpbitMarketClient;
import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.stereotype.Service;

@Service
public class AssetRegistrationFacade {

    private static final String SUPPORTED_QUOTE_CURRENCY_CODE = "KRW";
    private static final int MARKET_CODE_PART_COUNT = 2;

    private final UpbitMarketClient upbitMarketClient;
    private final AssetCommandService assetCommandService;

    public AssetRegistrationFacade(
            UpbitMarketClient upbitMarketClient,
            AssetCommandService assetCommandService
    ) {
        this.upbitMarketClient = upbitMarketClient;
        this.assetCommandService = assetCommandService;
    }

    public AssetRegistrationResult registerAsset(Exchange exchange, String exchangeMarketCode) {
        String[] currencyCodes = splitMarketCode(exchangeMarketCode);
        String quoteCurrencyCode = currencyCodes[0];
        String baseCurrencyCode = currencyCodes[1];

        if (!SUPPORTED_QUOTE_CURRENCY_CODE.equals(quoteCurrencyCode)) {
            throw new ApiException(ApiErrorCode.ASSET_UNSUPPORTED_QUOTE_CURRENCY);
        }

        UpbitMarketInformation marketInformation = upbitMarketClient
                .findMarket(exchangeMarketCode)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ASSET_NOT_SUPPORTED_BY_EXCHANGE));

        return assetCommandService.createAsset(
                exchange,
                marketInformation,
                baseCurrencyCode,
                quoteCurrencyCode
        );
    }

    private String[] splitMarketCode(String exchangeMarketCode) {
        if (exchangeMarketCode == null) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_MARKET_CODE);
        }

        String[] currencyCodes = exchangeMarketCode.split("-", -1);
        if (currencyCodes.length != MARKET_CODE_PART_COUNT
                || currencyCodes[0].isBlank()
                || currencyCodes[1].isBlank()) {
            throw new ApiException(ApiErrorCode.ASSET_INVALID_MARKET_CODE);
        }
        return currencyCodes;
    }
}
