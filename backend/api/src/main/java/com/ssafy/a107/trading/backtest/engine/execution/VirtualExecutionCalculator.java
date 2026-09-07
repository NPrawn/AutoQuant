package com.ssafy.a107.trading.backtest.engine.execution;

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

        if (quantity.signum() == 0) {
            return Optional.empty();
        }

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
}
