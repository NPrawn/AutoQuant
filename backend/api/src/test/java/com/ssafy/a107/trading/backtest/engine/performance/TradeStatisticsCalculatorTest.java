package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TradeStatisticsCalculatorTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );
    private static final Instant SIGNAL_AT = Instant.parse(
            "2026-01-02T00:00:00Z"
    );
    private static final Instant FILLED_AT = Instant.parse(
            "2026-01-02T01:00:00Z"
    );

    private final TradeStatisticsCalculator calculator =
            new TradeStatisticsCalculator();

    @DisplayName("전체 체결 수와 수수료 및 슬리피지 합계를 계산한다")
    @Test
    void calculateTradeStatistics() {
        List<VirtualFill> fills = List.of(
                fill(
                        TradeSide.BUY,
                        "100.123456789",
                        "1000.123456789"
                ),
                fill(
                        TradeSide.SELL,
                        "200.987654329",
                        "2000.987654329"
                )
        );

        TradeStatistics statistics = calculator.calculate(fills);

        assertThat(statistics.tradeCount()).isEqualTo(2);
        assertThat(statistics.totalFee())
                .isEqualByComparingTo("301.11111112");
        assertThat(statistics.totalSlippage())
                .isEqualByComparingTo("3001.11111112");
    }

    @DisplayName("체결이 없으면 모든 거래 통계 값은 0이다")
    @Test
    void returnZeroStatisticsWhenNoFillExists() {
        TradeStatistics statistics = calculator.calculate(List.of());

        assertThat(statistics.tradeCount()).isZero();
        assertThat(statistics.totalFee())
                .isEqualByComparingTo("0.00000000");
        assertThat(statistics.totalSlippage())
                .isEqualByComparingTo("0.00000000");
    }

    @DisplayName("체결 목록에 null이 포함될 수 없다")
    @Test
    void rejectNullFill() {
        List<VirtualFill> fills = new ArrayList<>();
        fills.add(fill(TradeSide.BUY, "100", "1000"));
        fills.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(fills));
    }

    @DisplayName("체결 목록은 null일 수 없다")
    @Test
    void rejectNullFillList() {
        assertThatNullPointerException()
                .isThrownBy(() -> calculator.calculate(null));
    }

    private VirtualFill fill(
            TradeSide side,
            String feeAmount,
            String slippageAmount
    ) {
        return new VirtualFill(
                new VirtualOrder(
                        ASSET_ID,
                        PositionSide.LONG,
                        side,
                        SIGNAL_AT,
                        FILLED_AT,
                        new BigDecimal("100000"),
                        new BigDecimal("200000")
                ),
                new BigDecimal("100000"),
                new BigDecimal("2"),
                new BigDecimal("200000"),
                new BigDecimal(feeAmount),
                new BigDecimal(slippageAmount)
        );
    }
}
