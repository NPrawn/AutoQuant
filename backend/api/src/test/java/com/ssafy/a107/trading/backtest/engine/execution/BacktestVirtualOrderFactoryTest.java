package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleFixture;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BacktestVirtualOrderFactoryTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ASSET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant SIGNAL_AT = Instant.parse("2026-01-02T01:00:00Z");

    private final BacktestVirtualOrderFactory factory =
            new BacktestVirtualOrderFactory();

    @DisplayName("리밸런싱 주문과 다음 봉 시가로 가상 주문을 생성한다")
    @Test
    void createVirtualOrderFromRebalanceOrder() {
        BacktestRebalanceOrder rebalanceOrder = new BacktestRebalanceOrder(
                ASSET_ID,
                TradeSide.SELL,
                new BigDecimal("250000")
        );
        MinuteCandle fillCandle = candle(
                ASSET_ID,
                SIGNAL_AT,
                new BigDecimal("123456")
        );

        VirtualOrder order = factory.create(
                rebalanceOrder,
                SIGNAL_AT,
                fillCandle
        );

        assertThat(order.assetId()).isEqualTo(ASSET_ID);
        assertThat(order.positionSide()).isEqualTo(PositionSide.LONG);
        assertThat(order.side()).isEqualTo(TradeSide.SELL);
        assertThat(order.signalAt()).isEqualTo(SIGNAL_AT);
        assertThat(order.filledAt()).isEqualTo(SIGNAL_AT);
        assertThat(order.referencePrice()).isEqualByComparingTo("123456");
        assertThat(order.requestedNotional()).isEqualByComparingTo("250000");
    }

    @DisplayName("리밸런싱 주문과 체결 봉의 종목이 다르면 가상 주문을 생성할 수 없다")
    @Test
    void rejectFillCandleForDifferentAsset() {
        BacktestRebalanceOrder rebalanceOrder = new BacktestRebalanceOrder(
                ASSET_ID,
                TradeSide.BUY,
                new BigDecimal("250000")
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(
                        rebalanceOrder,
                        SIGNAL_AT,
                        candle(OTHER_ASSET_ID, SIGNAL_AT, new BigDecimal("123456"))
                ))
                .withMessage("Fill candle asset must match rebalance order");
    }

    private MinuteCandle candle(
            UUID assetId,
            Instant openTime,
            BigDecimal openPrice
    ) {
        return MinuteCandleFixture.minuteCandle(
                assetId,
                Timeframe.ONE_HOUR.getIntervalMinutes(),
                openTime,
                openPrice,
                BigDecimal.ONE
        );
    }
}
