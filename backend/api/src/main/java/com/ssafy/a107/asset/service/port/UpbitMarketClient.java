package com.ssafy.a107.asset.service.port;

import java.time.Instant;
import java.util.Optional;

public interface UpbitMarketClient {

    Optional<UpbitMarketInformation> findMarket(String exchangeMarketCode);

    /*
     * 거래지원 시작일. Upbit 는 상장일을 공개하지 않아 가장 오래된 일봉의 날짜로 갈음한다.
     * 거래가 시작된 날에는 봉이 생기므로 실제 상장일과 같거나 하루 안쪽으로 차이 난다.
     * 확인하지 못하면 비워서 돌려준다. 등록을 막을 만큼 중요한 값은 아니다.
     */
    Optional<Instant> findTradingSupportStartedAt(String exchangeMarketCode);
}
