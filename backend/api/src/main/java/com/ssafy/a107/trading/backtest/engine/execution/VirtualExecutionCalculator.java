package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

@Component
public class VirtualExecutionCalculator {

    private static final int MONEY_SCALE = 8;

    private final SlippagePriceCalculator slippagePriceCalculator;
    private final OrderPrecisionCalculator orderPrecisionCalculator;
    private final TradeFeeCalculator tradeFeeCalculator;
    private final SlippageAmountCalculator slippageAmountCalculator;

    public VirtualExecutionCalculator(
            SlippagePriceCalculator slippagePriceCalculator,
            OrderPrecisionCalculator orderPrecisionCalculator,
            TradeFeeCalculator tradeFeeCalculator,
            SlippageAmountCalculator slippageAmountCalculator
    ) {
        this.slippagePriceCalculator = slippagePriceCalculator;
        this.orderPrecisionCalculator = orderPrecisionCalculator;
        this.tradeFeeCalculator = tradeFeeCalculator;
        this.slippageAmountCalculator = slippageAmountCalculator;
    }

    public Optional<VirtualFill> execute(
            VirtualOrder order,
            VirtualExecutionPolicy policy
    ) {
        return executeInternal(order, policy, null);
    }

    public Optional<VirtualFill> execute(
            VirtualOrder order,
            VirtualExecutionPolicy policy,
            BigDecimal availableCash
    ) {
        requireNonNegative(
                availableCash,
                "Available cash must not be negative"
        );

        return executeInternal(order, policy, availableCash);
    }

    private Optional<VirtualFill> executeInternal(
            VirtualOrder order,
            VirtualExecutionPolicy policy,
            BigDecimal availableCash
    ) {
        Objects.requireNonNull(order);
        Objects.requireNonNull(policy);

        BigDecimal slippedPrice = slippagePriceCalculator.calculate(
                order.referencePrice(),
                policy.slippageBps(),
                order.side()
        );

        BigDecimal executedPrice = orderPrecisionCalculator.roundPrice(
                slippedPrice,
                policy.priceTickSize(),
                order.side()
        );

        BigDecimal rawQuantity = order.requestedNotional().divide(
                executedPrice,
                MathContext.DECIMAL128
        );
        BigDecimal quantity = orderPrecisionCalculator.roundQuantity(
                rawQuantity,
                policy.quantityStepSize()
        );

        if (order.side() == TradeSide.BUY && availableCash != null) {
            quantity = limitBuyQuantity(
                    quantity,
                    executedPrice,
                    policy,
                    availableCash
            );
        }

        while (quantity.signum() > 0) {
            Optional<VirtualFill> fill = createFill(
                    order,
                    policy,
                    executedPrice,
                    quantity
            );

            if (fill.isEmpty()) {
                return Optional.empty();
            }

            if (order.side() != TradeSide.BUY || availableCash == null) {
                return fill;
            }

            BigDecimal requiredCash = fill.get().notional()
                    .add(fill.get().feeAmount());

            if (requiredCash.compareTo(availableCash) <= 0) {
                return fill;
            }

            quantity = quantity.subtract(policy.quantityStepSize());
        }

        return Optional.empty();
    }

    private BigDecimal limitBuyQuantity(
            BigDecimal requestedQuantity,
            BigDecimal executedPrice,
            VirtualExecutionPolicy policy,
            BigDecimal availableCash
    ) {
        BigDecimal costPerUnit = executedPrice.multiply(
                BigDecimal.ONE.add(policy.feeRate())
        );
        BigDecimal affordableRawQuantity = availableCash.divide(
                costPerUnit,
                MathContext.DECIMAL128
        );

        if (affordableRawQuantity.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal affordableQuantity = orderPrecisionCalculator.roundQuantity(
                affordableRawQuantity,
                policy.quantityStepSize()
        );

        return requestedQuantity.min(affordableQuantity);
    }

    private Optional<VirtualFill> createFill(
            VirtualOrder order,
            VirtualExecutionPolicy policy,
            BigDecimal executedPrice,
            BigDecimal quantity
    ) {
        BigDecimal notional = executedPrice.multiply(quantity)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (notional.compareTo(policy.minimumOrderAmount()) < 0) {
            return Optional.empty();
        }

        BigDecimal feeAmount = tradeFeeCalculator.calculate(
                notional,
                policy.feeRate()
        );

        BigDecimal slippageAmount = slippageAmountCalculator.calculate(
                order.referencePrice(),
                executedPrice,
                quantity
        );

        return Optional.of(new VirtualFill(
                order,
                executedPrice,
                quantity,
                notional,
                feeAmount,
                slippageAmount
        ));
    }

    private void requireNonNegative(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
