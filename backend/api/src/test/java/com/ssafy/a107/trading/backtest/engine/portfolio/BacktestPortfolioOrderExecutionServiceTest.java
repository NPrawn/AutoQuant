package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.KrwPriceTickSizeResolver;
import com.ssafy.a107.trading.backtest.engine.execution.OrderPrecisionCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.SlippageAmountCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.SlippagePriceCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.TradeFeeCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionPolicy;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestPortfolioOrderExecutionServiceTest {

    private static final UUID FIRST_ASSET_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    private static final UUID SECOND_ASSET_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    );
    private static final Instant ORDER_TIME = Instant.parse(
            "2026-01-02T00:00:00Z"
    );

    private final BacktestPortfolioOrderExecutionService service =
            new BacktestPortfolioOrderExecutionService(
                    new VirtualExecutionCalculator(
                            new SlippagePriceCalculator(),
                            new OrderPrecisionCalculator(
                                    new KrwPriceTickSizeResolver()
                            ),
                            new TradeFeeCalculator(),
                            new SlippageAmountCalculator()
                    )
            );

    @DisplayName("매수 주문보다 매도 주문을 먼저 체결해 매도 대금을 매수에 사용한다")
    @Test
    void executeSellOrdersBeforeBuyOrders() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000)
        );
        portfolio.applyBuy(fill(
                order(FIRST_ASSET_ID, TradeSide.BUY, BigDecimal.valueOf(500)),
                BigDecimal.valueOf(5)
        ));

        BacktestOrderExecutionResult result = service.execute(
                portfolio,
                List.of(
                        order(
                                SECOND_ASSET_ID,
                                TradeSide.BUY,
                                BigDecimal.valueOf(1_000)
                        ),
                        order(
                                FIRST_ASSET_ID,
                                TradeSide.SELL,
                                BigDecimal.valueOf(500)
                        )
                ),
                policy()
        );

        assertThat(result.fills())
                .extracting(fill -> fill.order().side())
                .containsExactly(TradeSide.SELL, TradeSide.BUY);
        assertThat(result.realizedTrades()).hasSize(1);
        assertThat(portfolio.findPosition(FIRST_ASSET_ID)).isEmpty();
        assertThat(portfolio.findPosition(SECOND_ASSET_ID))
                .map(BacktestPosition::quantity)
                .hasValueSatisfying(quantity -> assertThat(quantity)
                        .isEqualByComparingTo("10"));
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("0");
    }

    @DisplayName("보유하지 않은 종목의 매도 주문은 체결하지 않는다")
    @Test
    void skipSellOrderWithoutPosition() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000)
        );

        BacktestOrderExecutionResult result = service.execute(
                portfolio,
                List.of(order(
                        FIRST_ASSET_ID,
                        TradeSide.SELL,
                        BigDecimal.valueOf(500)
                )),
                policy()
        );

        assertThat(result.fills()).isEmpty();
        assertThat(result.realizedTrades()).isEmpty();
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1000");
    }

    private VirtualOrder order(
            UUID assetId,
            TradeSide side,
            BigDecimal requestedNotional
    ) {
        return new VirtualOrder(
                assetId,
                PositionSide.LONG,
                side,
                ORDER_TIME,
                ORDER_TIME,
                BigDecimal.valueOf(100),
                requestedNotional
        );
    }

    private VirtualFill fill(
            VirtualOrder order,
            BigDecimal quantity
    ) {
        BigDecimal notional = order.referencePrice().multiply(quantity);

        return new VirtualFill(
                order,
                order.referencePrice(),
                quantity,
                notional,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private VirtualExecutionPolicy policy() {
        return new VirtualExecutionPolicy(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE
        );
    }
}
