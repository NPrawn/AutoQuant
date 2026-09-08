package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VirtualFillTest {

    private static final BigDecimal EXECUTED_PRICE = BigDecimal.valueOf(100_500);
    private static final BigDecimal QUANTITY = BigDecimal.valueOf(2);
    private static final BigDecimal NOTIONAL = BigDecimal.valueOf(201_000);
    private static final BigDecimal FEE_AMOUNT = BigDecimal.valueOf(100.5);
    private static final BigDecimal SLIPPAGE_AMOUNT = BigDecimal.valueOf(1_000);

    @DisplayName("유효한 가상 체결 결과를 생성한다")
    @Test
    void createVirtualFill() {
        VirtualOrder order = order();

        VirtualFill fill = createFill(
                EXECUTED_PRICE,
                QUANTITY,
                NOTIONAL,
                FEE_AMOUNT,
                SLIPPAGE_AMOUNT
        );

        assertThat(fill.order()).isEqualTo(order);
        assertThat(fill.executedPrice()).isEqualByComparingTo(EXECUTED_PRICE);
        assertThat(fill.quantity()).isEqualByComparingTo(QUANTITY);
        assertThat(fill.notional()).isEqualByComparingTo(NOTIONAL);
        assertThat(fill.feeAmount()).isEqualByComparingTo(FEE_AMOUNT);
        assertThat(fill.slippageAmount()).isEqualByComparingTo(SLIPPAGE_AMOUNT);
    }

    @DisplayName("체결 가격은 0보다 커야 한다")
    @Test
    void rejectZeroExecutedPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createFill(
                        BigDecimal.ZERO,
                        QUANTITY,
                        NOTIONAL,
                        FEE_AMOUNT,
                        SLIPPAGE_AMOUNT
                ));
    }

    @DisplayName("체결 수량은 0보다 커야 한다")
    @Test
    void rejectZeroQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createFill(
                        EXECUTED_PRICE,
                        BigDecimal.ZERO,
                        NOTIONAL,
                        FEE_AMOUNT,
                        SLIPPAGE_AMOUNT
                ));
    }

    @DisplayName("체결 금액은 0보다 커야 한다")
    @Test
    void rejectZeroNotional() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createFill(
                        EXECUTED_PRICE,
                        QUANTITY,
                        BigDecimal.ZERO,
                        FEE_AMOUNT,
                        SLIPPAGE_AMOUNT
                ));
    }

    @DisplayName("수수료 금액은 음수일 수 없다")
    @Test
    void rejectNegativeFeeAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createFill(
                        EXECUTED_PRICE,
                        QUANTITY,
                        NOTIONAL,
                        BigDecimal.valueOf(-1),
                        SLIPPAGE_AMOUNT
                ));
    }

    @DisplayName("슬리피지 금액은 음수일 수 없다")
    @Test
    void rejectNegativeSlippageAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createFill(
                        EXECUTED_PRICE,
                        QUANTITY,
                        NOTIONAL,
                        FEE_AMOUNT,
                        BigDecimal.valueOf(-1)
                ));
    }

    private VirtualFill createFill(
            BigDecimal executedPrice,
            BigDecimal quantity,
            BigDecimal notional,
            BigDecimal feeAmount,
            BigDecimal slippageAmount
    ) {
        return new VirtualFill(
                order(),
                executedPrice,
                quantity,
                notional,
                feeAmount,
                slippageAmount
        );
    }

    private VirtualOrder order() {
        return new VirtualOrder(
                UUID.fromString("c56f7340-3dcc-41b8-a590-ef347dc53492"),
                PositionSide.LONG,
                TradeSide.BUY,
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-02T01:00:00Z"),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(200_000)
        );
    }
}
