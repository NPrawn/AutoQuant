package com.ssafy.a107.asset.infrastructure.upbit;

import com.ssafy.a107.asset.service.port.UpbitMarketClient;
import com.ssafy.a107.asset.service.port.UpbitMarketInformation;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Component
public class UpbitMarketWebClient implements UpbitMarketClient {

    private final WebClient webClient;
    private static final int CANDLE_PAGE_SIZE = 200;
    private static final int MAXIMUM_CANDLE_PAGES = 40;

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

    /*
     * 일봉을 200개씩 과거로 따라가 더 받을 것이 없을 때의 가장 오래된 봉을 상장일로 본다.
     * 업비트 개장일(2017-09-25)부터면 약 18번이면 끝난다. 정해진 횟수를 넘으면 포기한다.
     */
    @Override
    public Optional<Instant> findTradingSupportStartedAt(String exchangeMarketCode) {
        String to = null;
        Instant oldest = null;
        for (int page = 0; page < MAXIMUM_CANDLE_PAGES; page++) {
            List<UpbitDailyCandleResponse> candles = requestDailyCandles(exchangeMarketCode, to);
            if (candles.isEmpty()) {
                return Optional.ofNullable(oldest);
            }

            String last = candles.getLast().candleDateTimeUtc();
            if (last == null || last.isBlank()) {
                return Optional.ofNullable(oldest);
            }
            oldest = LocalDateTime.parse(last).toInstant(ZoneOffset.UTC);
            to = last;
        }
        return Optional.ofNullable(oldest);
    }

    private List<UpbitDailyCandleResponse> requestDailyCandles(String exchangeMarketCode, String to) {
        try {
            List<UpbitDailyCandleResponse> candles = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/v1/candles/days")
                                .queryParam("market", exchangeMarketCode)
                                .queryParam("count", CANDLE_PAGE_SIZE);
                        if (to != null) {
                            uriBuilder.queryParam("to", to);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToFlux(UpbitDailyCandleResponse.class)
                    .collectList()
                    .timeout(properties.timeout())
                    .block();

            return candles == null ? List.of() : candles;
        } catch (RuntimeException exception) {
            /* 상장일은 없어도 등록을 막지 않는다. 조회가 실패하면 그때까지 찾은 값을 쓴다. */
            return List.of();
        }
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
