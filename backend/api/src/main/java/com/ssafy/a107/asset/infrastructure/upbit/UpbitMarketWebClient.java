package com.ssafy.a107.asset.infrastructure.upbit;

import com.ssafy.a107.asset.service.port.UpbitMarketClient;
import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Component
public class UpbitMarketWebClient implements UpbitMarketClient {

    private final WebClient webClient;
    private final UpbitProperties properties;

    public UpbitMarketWebClient(
            @Qualifier("upbitWebClient") WebClient webClient,
            UpbitProperties properties
    ) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Optional<UpbitMarketInformation> findMarket(String exchangeMarketCode) {
        List<UpbitMarketResponse> markets = requestMarkets();

        return markets.stream()
                .filter(market -> exchangeMarketCode.equals(market.exchangeMarketCode()))
                .findFirst()
                .map(this::toMarketInformation);
    }

    private List<UpbitMarketResponse> requestMarkets() {
        try {
            List<UpbitMarketResponse> markets = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/market/all")
                            .queryParam("is_details", false)
                            .build())
                    .retrieve()
                    .bodyToFlux(UpbitMarketResponse.class)
                    .collectList()
                    .timeout(properties.timeout())
                    .block();

            if (markets == null) {
                throw new ApiException(ApiErrorCode.ASSET_EXCHANGE_INVALID_RESPONSE);
            }
            return markets;
        } catch (ApiException exception) {
            throw exception;
        } catch (WebClientException exception) {
            throw new ApiException(ApiErrorCode.ASSET_EXCHANGE_UNAVAILABLE, exception);
        } catch (RuntimeException exception) {
            if (hasTimeoutCause(exception)) {
                throw new ApiException(ApiErrorCode.ASSET_EXCHANGE_UNAVAILABLE, exception);
            }
            throw new ApiException(ApiErrorCode.ASSET_EXCHANGE_INVALID_RESPONSE, exception);
        }
    }

    private UpbitMarketInformation toMarketInformation(UpbitMarketResponse response) {
        if (isBlank(response.exchangeMarketCode())
                || isBlank(response.koreanName())
                || isBlank(response.englishName())) {
            throw new ApiException(ApiErrorCode.ASSET_EXCHANGE_INVALID_RESPONSE);
        }

        return new UpbitMarketInformation(
                response.exchangeMarketCode(),
                response.koreanName(),
                response.englishName()
        );
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
