package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.strategy.engine.portfolio.TargetAssetWeight;
import com.ssafy.a107.strategy.engine.portfolio.TargetPortfolio;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestRebalanceOrderPlannerTest {

    private static final UUID FIRST_ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SECOND_ASSET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final BacktestRebalanceOrderPlanner planner =
            new BacktestRebalanceOrderPlanner(
                    new PortfolioValuationCalculator()
            );

    @DisplayName("신규 목표 종목의 목표 비중만큼 매수 주문을 생성한다")
    @Test
    void createBuyOrderForNewTargetAsset() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                new BigDecimal("1000")
        );

        List<BacktestRebalanceOrder> orders = planner.plan(
                portfolio,
                targetPortfolio(SECOND_ASSET_ID, "0.5"),
                Map.of(SECOND_ASSET_ID, new BigDecimal("100"))
        );

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.assetId()).isEqualTo(SECOND_ASSET_ID);
            assertThat(order.side()).isEqualTo(TradeSide.BUY);
            assertThat(order.requestedNotional()).isEqualByComparingTo("500");
        });
    }

    @DisplayName("기존 보유 종목의 목표 비중을 축소하는 매도 주문을 생성한다")
    @Test
    void createSellOrderToReduceExistingPosition() {
        BacktestPortfolio portfolio = portfolioWithBuy(
                FIRST_ASSET_ID,
                new BigDecimal("5")
        );

        List<BacktestRebalanceOrder> orders = planner.plan(
                portfolio,
                targetPortfolio(FIRST_ASSET_ID, "0.25"),
                Map.of(FIRST_ASSET_ID, new BigDecimal("100"))
        );

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.assetId()).isEqualTo(FIRST_ASSET_ID);
            assertThat(order.side()).isEqualTo(TradeSide.SELL);
            assertThat(order.requestedNotional()).isEqualByComparingTo("250");
        });
    }

    @DisplayName("목표 포트폴리오에서 제외된 보유 종목은 전량 매도 주문을 생성한다")
    @Test
    void createFullSellOrderForRemovedAsset() {
        BacktestPortfolio portfolio = portfolioWithBuy(
                FIRST_ASSET_ID,
                new BigDecimal("5")
        );

        List<BacktestRebalanceOrder> orders = planner.plan(
                portfolio,
                new TargetPortfolio(List.of(), BigDecimal.ONE),
                Map.of(FIRST_ASSET_ID, new BigDecimal("100"))
        );

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.assetId()).isEqualTo(FIRST_ASSET_ID);
            assertThat(order.side()).isEqualTo(TradeSide.SELL);
            assertThat(order.requestedNotional()).isEqualByComparingTo("500");
        });
    }

    @DisplayName("리밸런싱 주문은 매도 주문을 매수 주문보다 먼저 생성한다")
    @Test
    void placeSellOrdersBeforeBuyOrders() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                new BigDecimal("1000")
        );
        applyBuy(portfolio, FIRST_ASSET_ID, new BigDecimal("3"));
        applyBuy(portfolio, SECOND_ASSET_ID, new BigDecimal("2"));

        List<BacktestRebalanceOrder> orders = planner.plan(
                portfolio,
                targetPortfolio(SECOND_ASSET_ID, "0.7"),
                Map.of(
                        FIRST_ASSET_ID, new BigDecimal("100"),
                        SECOND_ASSET_ID, new BigDecimal("100")
                )
        );

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).assetId()).isEqualTo(FIRST_ASSET_ID);
        assertThat(orders.get(0).side()).isEqualTo(TradeSide.SELL);
        assertThat(orders.get(0).requestedNotional()).isEqualByComparingTo("300");
        assertThat(orders.get(1).assetId()).isEqualTo(SECOND_ASSET_ID);
        assertThat(orders.get(1).side()).isEqualTo(TradeSide.BUY);
        assertThat(orders.get(1).requestedNotional()).isEqualByComparingTo("500");
    }

    private BacktestPortfolio portfolioWithBuy(
            UUID assetId,
            BigDecimal quantity
    ) {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                new BigDecimal("1000")
        );
        applyBuy(portfolio, assetId, quantity);
        return portfolio;
    }

    private TargetPortfolio targetPortfolio(
            UUID assetId,
            String targetWeight
    ) {
        return new TargetPortfolio(
                List.of(new TargetAssetWeight(
                        assetId,
                        new BigDecimal(targetWeight)
                )),
                BigDecimal.ONE.subtract(new BigDecimal(targetWeight))
        );
    }

    private void applyBuy(
            BacktestPortfolio portfolio,
            UUID assetId,
            BigDecimal quantity
    ) {
        BigDecimal price = new BigDecimal("100");
        BigDecimal notional = price.multiply(quantity);
        VirtualOrder order = new VirtualOrder(
                assetId,
                PositionSide.LONG,
                TradeSide.BUY,
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-02T01:00:00Z"),
                price,
                notional
        );

        portfolio.applyBuy(new VirtualFill(
                order,
                price,
                quantity,
                notional,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));
    }
}
