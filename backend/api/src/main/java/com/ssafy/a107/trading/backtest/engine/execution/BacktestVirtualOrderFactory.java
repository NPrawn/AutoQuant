package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestRebalanceOrder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class BacktestVirtualOrderFactory {

    public VirtualOrder create(
            BacktestRebalanceOrder rebalanceOrder,
            Instant signalAt,
            MinuteCandle fillCandle
    ) {
        Objects.requireNonNull(rebalanceOrder);
        Objects.requireNonNull(signalAt);
        Objects.requireNonNull(fillCandle);

        if (!rebalanceOrder.assetId().equals(
                fillCandle.getId().getAssetId()
        )) {
            throw new IllegalArgumentException(
                    "Fill candle asset must match rebalance order"
            );
        }

        return new VirtualOrder(
                rebalanceOrder.assetId(),
                PositionSide.LONG,
                rebalanceOrder.side(),
                signalAt,
                fillCandle.getId().getOpenTime(),
                fillCandle.getOpenPrice(),
                rebalanceOrder.requestedNotional()
        );
    }
}