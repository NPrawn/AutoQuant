package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VirtualExecutionCalculatorTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );
    private static final Instant SIGNAL_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-01-02T01:00:00Z");
    private static final BigDecimal REFERENCE_PRICE = BigDecimal.valueOf(100_000);

    private final VirtualExecutionCalculator calculator = new VirtualExecutionCalculator(
            new SlippagePriceCalculator(),
            new OrderPrecisionCalculator(),
            new TradeFeeCalculator(),
            new SlippageAmountCalculator()
    );

    @DisplayName("매수 주문에 슬리피지와 수수료 및 주문 단위를 적용한다")
    @Test
    void executeBuyOrder() {
        VirtualOrder order = order(TradeSide.BUY, BigDecimal.valueOf(500_000));

        VirtualFill fill = calculator.execute(order, policy()).orElseThrow();

        assertThat(fill.executedPrice()).isEqualByComparingTo("100050");
        assertThat(fill.quantity()).isEqualByComparingTo("4.9975");
        assertThat(fill.notional()).isEqualByComparingTo("499999.87500000");
        assertThat(fill.feeAmount()).isEqualByComparingTo("249.99993750");
        assertThat(fill.slippageAmount()).isEqualByComparingTo("249.87500000");
    }

    @DisplayName("매도 주문에 불리한 방향의 슬리피지를 적용한다")
    @Test
    void executeSellOrder() {
        VirtualOrder order = order(TradeSide.SELL, BigDecimal.valueOf(500_000));

        VirtualFill fill = calculator.execute(order, policy()).orElseThrow();

        assertThat(fill.executedPrice()).isEqualByComparingTo("99950");
        assertThat(fill.quantity()).isEqualByComparingTo("5.0025");
        assertThat(fill.notional()).isEqualByComparingTo("499999.87500000");
        assertThat(fill.feeAmount()).isEqualByComparingTo("249.99993750");
        assertThat(fill.slippageAmount()).isEqualByComparingTo("250.12500000");
    }

    @DisplayName("주문 단위 적용 후 체결금액이 최소 주문금액보다 작으면 체결하지 않는다")
    @Test
    void skipOrderBelowMinimumOrderAmount() {
        VirtualOrder order = order(TradeSide.BUY, BigDecimal.valueOf(4_999));

        Optional<VirtualFill> fill = calculator.execute(order, policy());

        assertThat(fill).isEmpty();
    }

    @DisplayName("주문 희망 수량이 수량 주문 단위보다 작으면 체결하지 않는다")
    @Test
    void skipOrderBelowQuantityStepSize() {
        VirtualOrder order = order(TradeSide.BUY, BigDecimal.valueOf(5_000));
        VirtualExecutionPolicy policy = new VirtualExecutionPolicy(
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(5_000),
                BigDecimal.TEN,
                BigDecimal.ONE
        );

        Optional<VirtualFill> fill = calculator.execute(order, policy);

        assertThat(fill).isEmpty();
    }

    @DisplayName("매수 가능 현금이 요청 금액보다 적으면 수수료를 포함한 한도 내에서 체결한다")
    @Test
    void executeBuyOrderWithinAvailableCash() {
        BigDecimal availableCash = BigDecimal.valueOf(100_000);

        VirtualFill fill = calculator.execute(
                order(TradeSide.BUY, BigDecimal.valueOf(500_000)),
                policy(),
                availableCash
        ).orElseThrow();

        assertThat(fill.quantity()).isEqualByComparingTo("0.9990");
        assertThat(fill.notional().add(fill.feeAmount()))
                .isLessThanOrEqualTo(availableCash);
    }

    @DisplayName("매수 가능 현금으로 최소 주문 금액을 충족하지 못하면 체결하지 않는다")
    @Test
    void skipBuyOrderBelowMinimumAmountAfterCashLimit() {
        Optional<VirtualFill> fill = calculator.execute(
                order(TradeSide.BUY, BigDecimal.valueOf(500_000)),
                policy(),
                BigDecimal.valueOf(5_000)
        );

        assertThat(fill).isEmpty();
    }

    @DisplayName("매수 가능 현금은 음수일 수 없다")
    @Test
    void rejectNegativeAvailableCash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.execute(
                        order(TradeSide.BUY, BigDecimal.valueOf(500_000)),
                        policy(),
                        BigDecimal.ONE.negate()
                ))
                .withMessage("Available cash must not be negative");
    }

    private VirtualOrder order(
            TradeSide side,
            BigDecimal requestedNotional
    ) {
        return new VirtualOrder(
                ASSET_ID,
                PositionSide.LONG,
                side,
                SIGNAL_AT,
                FILLED_AT,
                REFERENCE_PRICE,
                requestedNotional
        );
    }

    private VirtualExecutionPolicy policy() {
        return new VirtualExecutionPolicy(
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(5_000),
                BigDecimal.TEN,
                new BigDecimal("0.0001")
        );
    }
}
