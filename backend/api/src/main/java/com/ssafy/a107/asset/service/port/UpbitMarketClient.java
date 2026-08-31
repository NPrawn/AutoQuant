package com.ssafy.a107.asset.service.port;

import java.util.Optional;

public interface UpbitMarketClient {

    Optional<UpbitMarketInformation> findMarket(String exchangeMarketCode);
}
