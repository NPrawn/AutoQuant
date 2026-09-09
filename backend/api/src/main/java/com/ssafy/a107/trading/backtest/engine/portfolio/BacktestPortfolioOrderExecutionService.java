package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionCalculator;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualExecutionPolicy;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class BacktestPortfolioOrderExecutionService {

    private final VirtualExecutionCalculator virtualExecutionCalculator;

    public BacktestPortfolioOrderExecutionService(
            VirtualExecutionCalculator virtualExecutionCalculator
    ) {
        this.virtualExecutionCalculator = Objects.requireNonNull(
                virtualExecutionCalculator
        );
    }

    public BacktestOrderExecutionResult execute(
            BacktestPortfolio portfolio,
            List<VirtualOrder> orders,
            VirtualExecutionPolicy policy
    ) {
        Objects.requireNonNull(portfolio);
        Objects.requireNonNull(orders);
        Objects.requireNonNull(policy);

        List<VirtualFill> fills = new ArrayList<>();
        List<RealizedTradeResult> realizedTrades = new ArrayList<>();

        for (VirtualOrder order : orderedByExecutionPriority(orders)) {
            Optional<VirtualFill> fill = executeOrder(
                    portfolio,
                    order,
                    policy
            );

            if (fill.isEmpty()) {
                continue;
            }

            applyFill(portfolio, fill.get(), realizedTrades);
            fills.add(fill.get());
        }

        return new BacktestOrderExecutionResult(fills, realizedTrades);
    }

    private List<VirtualOrder> orderedByExecutionPriority(
            List<VirtualOrder> orders
    ) {
        List<VirtualOrder> sellOrders = new ArrayList<>();
        List<VirtualOrder> buyOrders = new ArrayList<>();

        for (VirtualOrder order : orders) {
            VirtualOrder requiredOrder = Objects.requireNonNull(order);
            if (requiredOrder.side() == TradeSide.SELL) {
                sellOrders.add(requiredOrder);
            } else {
                buyOrders.add(requiredOrder);
            }
        }

        sellOrders.addAll(buyOrders);
        return List.copyOf(sellOrders);
    }

    private Optional<VirtualFill> executeOrder(
            BacktestPortfolio portfolio,
            VirtualOrder order,
            VirtualExecutionPolicy policy
    ) {
        if (order.side() == TradeSide.BUY) {
            return virtualExecutionCalculator.execute(
                    order,
                    policy,
                    portfolio.getCashBalance()
            );
        }

        BigDecimal availableQuantity = portfolio.findPosition(
                        order.assetId()
                )
                .map(BacktestPosition::quantity)
                .orElse(BigDecimal.ZERO);

        return virtualExecutionCalculator.executeSell(
                order,
                policy,
                availableQuantity
        );
    }

    private void applyFill(
            BacktestPortfolio portfolio,
            VirtualFill fill,
            List<RealizedTradeResult> realizedTrades
    ) {
        if (fill.order().side() == TradeSide.BUY) {
            portfolio.applyBuy(fill);
            return;
        }

        realizedTrades.add(portfolio.applySell(fill));
    }
}
