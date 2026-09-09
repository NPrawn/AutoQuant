package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BacktestRebalanceOrderTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @DisplayName("양수 주문 금액으로 리밸런싱 주문을 생성한다")
    @Test
    void createRebalanceOrder() {
        BacktestRebalanceOrder order = new BacktestRebalanceOrder(
                ASSET_ID,
                TradeSide.BUY,
                new BigDecimal("100000")
        );

        assertThat(order.assetId()).isEqualTo(ASSET_ID);
        assertThat(order.side()).isEqualTo(TradeSide.BUY);
        assertThat(order.requestedNotional()).isEqualByComparingTo("100000");
    }

    @DisplayName("0 이하 주문 금액은 리밸런싱 주문으로 생성할 수 없다")
    @Test
    void rejectNonPositiveRequestedNotional() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestRebalanceOrder(
                        ASSET_ID,
                        TradeSide.SELL,
                        BigDecimal.ZERO
                ))
                .withMessage("Requested notional must be greater than zero");
    }
}
