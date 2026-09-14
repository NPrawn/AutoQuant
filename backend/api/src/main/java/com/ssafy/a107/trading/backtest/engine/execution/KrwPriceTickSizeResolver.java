package com.ssafy.a107.trading.backtest.engine.execution;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class KrwPriceTickSizeResolver {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal FIVE_HUNDRED_THOUSAND = BigDecimal.valueOf(500_000);
    private static final BigDecimal ONE_HUNDRED_THOUSAND = BigDecimal.valueOf(100_000);
    private static final BigDecimal FIFTY_THOUSAND = BigDecimal.valueOf(50_000);
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);
    private static final BigDecimal FIVE_THOUSAND = BigDecimal.valueOf(5_000);
    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TENTH = new BigDecimal("0.1");
    private static final BigDecimal HUNDREDTH = new BigDecimal("0.01");
    private static final BigDecimal THOUSANDTH = new BigDecimal("0.001");
    private static final BigDecimal TEN_THOUSANDTH = new BigDecimal("0.0001");
    private static final BigDecimal HUNDRED_THOUSANDTH = new BigDecimal("0.00001");

    public BigDecimal resolve(BigDecimal price) {
        requirePositive(price);

        if (price.compareTo(ONE_MILLION) >= 0) {
            return BigDecimal.valueOf(1_000);
        }
        if (price.compareTo(FIVE_HUNDRED_THOUSAND) >= 0) {
            return BigDecimal.valueOf(500);
        }
        if (price.compareTo(ONE_HUNDRED_THOUSAND) >= 0) {
            return BigDecimal.valueOf(100);
        }
        if (price.compareTo(FIFTY_THOUSAND) >= 0) {
            return BigDecimal.valueOf(50);
        }
        if (price.compareTo(TEN_THOUSAND) >= 0) {
            return BigDecimal.TEN;
        }
        if (price.compareTo(FIVE_THOUSAND) >= 0) {
            return BigDecimal.valueOf(5);
        }
        if (price.compareTo(ONE_THOUSAND) >= 0) {
            return BigDecimal.ONE;
        }
        if (price.compareTo(ONE_HUNDRED) >= 0) {
            return BigDecimal.ONE;
        }
        if (price.compareTo(TEN) >= 0) {
            return TENTH;
        }
        if (price.compareTo(ONE) >= 0) {
            return HUNDREDTH;
        }
        if (price.compareTo(TENTH) >= 0) {
            return THOUSANDTH;
        }
        if (price.compareTo(HUNDREDTH) >= 0) {
            return TEN_THOUSANDTH;
        }
        if (price.compareTo(THOUSANDTH) >= 0) {
            return HUNDRED_THOUSANDTH;
        }
        if (price.compareTo(TEN_THOUSANDTH) >= 0) {
            return new BigDecimal("0.000001");
        }
        if (price.compareTo(HUNDRED_THOUSANDTH) >= 0) {
            return new BigDecimal("0.0000001");
        }
        return new BigDecimal("0.00000001");
    }

    private void requirePositive(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
    }
}
