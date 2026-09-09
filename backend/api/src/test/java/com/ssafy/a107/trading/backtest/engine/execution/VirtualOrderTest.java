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

class VirtualOrderTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );
    private static final Instant SIGNAL_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-01-02T01:00:00Z");
    private static final BigDecimal REFERENCE_PRICE = BigDecimal.valueOf(100_000);
    private static final BigDecimal REQUESTED_NOTIONAL = BigDecimal.valueOf(500_000);

    @DisplayName("유효한 가상 체결 주문을 생성한다")
    @Test
    void createVirtualOrder() {
        VirtualOrder order = createOrder(
                FILLED_AT,
                REFERENCE_PRICE,
                REQUESTED_NOTIONAL
        );

        assertThat(order.assetId()).isEqualTo(ASSET_ID);
        assertThat(order.positionSide()).isEqualTo(PositionSide.LONG);
        assertThat(order.side()).isEqualTo(TradeSide.BUY);
        assertThat(order.signalAt()).isEqualTo(SIGNAL_AT);
        assertThat(order.filledAt()).isEqualTo(FILLED_AT);
        assertThat(order.referencePrice()).isEqualByComparingTo(REFERENCE_PRICE);
        assertThat(order.requestedNotional()).isEqualByComparingTo(REQUESTED_NOTIONAL);
    }

    @DisplayName("체결 시각은 신호 시각과 같을 수 있다")
    @Test
    void createVirtualOrderAtSignalTime() {
        VirtualOrder order = createOrder(
                SIGNAL_AT,
                REFERENCE_PRICE,
                REQUESTED_NOTIONAL
        );

        assertThat(order.filledAt()).isEqualTo(SIGNAL_AT);
    }

    @DisplayName("체결 시각은 신호 시각보다 이를 수 없다")
    @Test
    void rejectFillBeforeSignalTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createOrder(
                        SIGNAL_AT.minusSeconds(1),
                        REFERENCE_PRICE,
                        REQUESTED_NOTIONAL
                ))
                .withMessage("Filled at must not be before signal at");
    }

    @DisplayName("기준 가격은 0보다 커야 한다")
    @Test
    void rejectZeroReferencePrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createOrder(
                        FILLED_AT,
                        BigDecimal.ZERO,
                        REQUESTED_NOTIONAL
                ));
    }

    @DisplayName("주문 희망 금액은 0보다 커야 한다")
    @Test
    void rejectZeroRequestedNotional() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createOrder(
                        FILLED_AT,
                        REFERENCE_PRICE,
                        BigDecimal.ZERO
                ));
    }

    private VirtualOrder createOrder(
            Instant filledAt,
            BigDecimal referencePrice,
            BigDecimal requestedNotional
    ) {
        return new VirtualOrder(
                ASSET_ID,
                PositionSide.LONG,
                TradeSide.BUY,
                SIGNAL_AT,
                filledAt,
                referencePrice,
                requestedNotional
        );
    }
}
