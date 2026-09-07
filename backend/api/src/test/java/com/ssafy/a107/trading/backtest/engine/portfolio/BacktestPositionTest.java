package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BacktestPositionTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );

    @DisplayName("유효한 백테스트 포지션을 생성한다")
    @Test
    void createBacktestPosition() {
        BacktestPosition position = new BacktestPosition(
                ASSET_ID,
                PositionSide.LONG,
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(100_000)
        );

        assertThat(position.assetId()).isEqualTo(ASSET_ID);
        assertThat(position.positionSide()).isEqualTo(PositionSide.LONG);
        assertThat(position.quantity()).isEqualByComparingTo("2");
        assertThat(position.averageEntryPrice()).isEqualByComparingTo("100000");
        assertThat(position.costBasis()).isEqualByComparingTo("200000.00000000");
    }

    @DisplayName("매수 수수료를 포함한 취득원가로 포지션을 생성한다")
    @Test
    void createPositionWithCostBasis() {
        BacktestPosition position = new BacktestPosition(
                ASSET_ID,
                PositionSide.LONG,
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(200_100)
        );

        assertThat(position.costBasis()).isEqualByComparingTo("200100");
    }

    @DisplayName("포지션 수량은 0보다 커야 한다")
    @Test
    void rejectZeroQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestPosition(
                        ASSET_ID,
                        PositionSide.LONG,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(100_000)
                ));
    }

    @DisplayName("평균 진입가는 0보다 커야 한다")
    @Test
    void rejectZeroAverageEntryPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestPosition(
                        ASSET_ID,
                        PositionSide.LONG,
                        BigDecimal.valueOf(2),
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("취득원가는 0보다 커야 한다")
    @Test
    void rejectZeroCostBasis() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestPosition(
                        ASSET_ID,
                        PositionSide.LONG,
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("취득원가는 null일 수 없다")
    @Test
    void rejectNullCostBasis() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestPosition(
                        ASSET_ID,
                        PositionSide.LONG,
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(100_000),
                        null
                ));
    }
}
