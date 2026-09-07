package com.ssafy.a107.trading.backtest.engine.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VirtualExecutionPolicyTest {

    private static final BigDecimal FEE_RATE = BigDecimal.valueOf(0.0005);
    private static final BigDecimal SLIPPAGE_BPS = BigDecimal.valueOf(5);
    private static final BigDecimal MINIMUM_ORDER_AMOUNT = BigDecimal.valueOf(5_000);
    private static final BigDecimal PRICE_TICK_SIZE = BigDecimal.valueOf(1_000);
    private static final BigDecimal QUANTITY_STEP_SIZE = new BigDecimal("0.00000001");

    @DisplayName("유효한 가상 체결 정책을 생성한다")
    @Test
    void createVirtualExecutionPolicy() {
        VirtualExecutionPolicy policy = validPolicy();

        assertThat(policy.feeRate()).isEqualByComparingTo(FEE_RATE);
        assertThat(policy.slippageBps()).isEqualByComparingTo(SLIPPAGE_BPS);
        assertThat(policy.minimumOrderAmount()).isEqualByComparingTo(MINIMUM_ORDER_AMOUNT);
        assertThat(policy.priceTickSize()).isEqualByComparingTo(PRICE_TICK_SIZE);
        assertThat(policy.quantityStepSize()).isEqualByComparingTo(QUANTITY_STEP_SIZE);
    }

    @DisplayName("수수료율은 음수일 수 없다")
    @Test
    void rejectNegativeFeeRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VirtualExecutionPolicy(
                        BigDecimal.valueOf(-0.0005),
                        SLIPPAGE_BPS,
                        MINIMUM_ORDER_AMOUNT,
                        PRICE_TICK_SIZE,
                        QUANTITY_STEP_SIZE
                ));
    }

    @DisplayName("슬리피지는 음수일 수 없다")
    @Test
    void rejectNegativeSlippageBps() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VirtualExecutionPolicy(
                        FEE_RATE,
                        BigDecimal.valueOf(-1),
                        MINIMUM_ORDER_AMOUNT,
                        PRICE_TICK_SIZE,
                        QUANTITY_STEP_SIZE
                ));
    }

    @DisplayName("슬리피지는 10000 bps보다 작아야 한다")
    @Test
    void rejectSlippageBpsAtUpperBound() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VirtualExecutionPolicy(
                        FEE_RATE,
                        BigDecimal.valueOf(10_000),
                        MINIMUM_ORDER_AMOUNT,
                        PRICE_TICK_SIZE,
                        QUANTITY_STEP_SIZE
                ));
    }

    @DisplayName("최소 주문금액은 0보다 커야 한다")
    @Test
    void rejectZeroMinimumOrderAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VirtualExecutionPolicy(
                        FEE_RATE,
                        SLIPPAGE_BPS,
                        BigDecimal.ZERO,
                        PRICE_TICK_SIZE,
                        QUANTITY_STEP_SIZE
                ));
    }

    @DisplayName("가격 주문 단위는 0보다 커야 한다")
    @Test
    void rejectZeroPriceTickSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VirtualExecutionPolicy(
                        FEE_RATE,
                        SLIPPAGE_BPS,
                        MINIMUM_ORDER_AMOUNT,
                        BigDecimal.ZERO,
                        QUANTITY_STEP_SIZE
                ));
    }

    @DisplayName("수량 주문 단위는 0보다 커야 한다")
    @Test
    void rejectZeroQuantityStepSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VirtualExecutionPolicy(
                        FEE_RATE,
                        SLIPPAGE_BPS,
                        MINIMUM_ORDER_AMOUNT,
                        PRICE_TICK_SIZE,
                        BigDecimal.ZERO
                ));
    }

    private VirtualExecutionPolicy validPolicy() {
        return new VirtualExecutionPolicy(
                FEE_RATE,
                SLIPPAGE_BPS,
                MINIMUM_ORDER_AMOUNT,
                PRICE_TICK_SIZE,
                QUANTITY_STEP_SIZE
        );
    }
}
