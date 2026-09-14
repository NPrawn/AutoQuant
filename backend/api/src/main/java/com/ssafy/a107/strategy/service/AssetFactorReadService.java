package com.ssafy.a107.strategy.service;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.service.MinuteCandleReadService;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.engine.factor.Ta4jFactorCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * 차트가 보여 줄 종목의 현재 팩터 값이다. 전략 엔진이 쓰는 계산기를 그대로 불러 화면과 엔진이 같은 숫자를 본다.
 * 화면이 따로 계산하면 사용자가 보고 조건을 정한 값과 실제 선별 결과가 어긋난다.
 */
@Service
@Transactional(readOnly = true)
public class AssetFactorReadService {

    // 기간은 서버가 정한다. 화면은 고르지 않고 응답에 실린 기간을 그대로 보인다.
    private static final List<FactorOperand> DEFAULT_FACTORS = List.of(
            new FactorOperand(FactorType.RETURN, 20),
            new FactorOperand(FactorType.RSI, 14),
            new FactorOperand(FactorType.SMA_DISPARITY, 20),
            new FactorOperand(FactorType.VOLATILITY, 20),
            new FactorOperand(FactorType.VOLUME_RATIO, 20),
            new FactorOperand(FactorType.DRAWDOWN, 60)
    );

    // 변동성은 종가로 수익률을 먼저 만들어 기간보다 봉이 더 든다. 기간 최댓값에 여유분을 더해 읽는다.
    private static final int EXTRA_CANDLES = 2;

    private final MinuteCandleReadService candleReadService;
    private final Ta4jFactorCalculator factorCalculator;

    public AssetFactorReadService(MinuteCandleReadService candleReadService, Ta4jFactorCalculator factorCalculator) {
        this.candleReadService = candleReadService;
        this.factorCalculator = factorCalculator;
    }

    public AssetFactorSnapshot findCurrent(UUID assetId, Timeframe timeframe) {
        List<MinuteCandle> candles = candleReadService.findRecentCandles(
                assetId, timeframe.getIntervalMinutes(), candleCount());

        List<AssetFactorValue> values = DEFAULT_FACTORS.stream()
                .map(factor -> new AssetFactorValue(factor.factor(), factor.period(),
                        factorCalculator.calculate(factor.factor(), factor.period(), candles).orElse(null)))
                .toList();
        return new AssetFactorSnapshot(latestOpenTime(candles), values);
    }

    private int candleCount() {
        return DEFAULT_FACTORS.stream().mapToInt(FactorOperand::period).max().orElseThrow() + EXTRA_CANDLES;
    }

    private Instant latestOpenTime(List<MinuteCandle> candles) {
        return candles.isEmpty() ? null : candles.getLast().getId().getOpenTime();
    }
}
