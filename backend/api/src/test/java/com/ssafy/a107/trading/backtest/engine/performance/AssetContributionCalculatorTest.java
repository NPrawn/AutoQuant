package com.ssafy.a107.trading.backtest.engine.performance;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.engine.portfolio.BacktestPosition;
import com.ssafy.a107.trading.backtest.engine.portfolio.RealizedTradeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AssetContributionCalculatorTest {

    private static final UUID FIRST_ASSET_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID SECOND_ASSET_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );

    private final AssetContributionCalculator calculator =
            new AssetContributionCalculator();

    @DisplayName("자산별 실현손익과 미실현손익을 합산해 기여도를 계산한다")
    @Test
    void calculateAssetContributions() {
        List<RealizedTradeResult> realizedTrades = List.of(
                new RealizedTradeResult(
                        SECOND_ASSET_ID,
                        BigDecimal.ONE,
                        new BigDecimal("120000"),
                        new BigDecimal("100000")
                ),
                new RealizedTradeResult(
                        FIRST_ASSET_ID,
                        BigDecimal.ONE,
                        new BigDecimal("90000"),
                        new BigDecimal("100000")
                )
        );
        List<BacktestPosition> openPositions = List.of(
                new BacktestPosition(
                        SECOND_ASSET_ID,
                        PositionSide.LONG,
                        BigDecimal.ONE,
                        new BigDecimal("100000"),
                        new BigDecimal("100050")
                ),
                new BacktestPosition(
                        FIRST_ASSET_ID,
                        PositionSide.LONG,
                        new BigDecimal("2"),
                        new BigDecimal("50000"),
                        new BigDecimal("100000")
                )
        );

        List<AssetContribution> contributions = calculator.calculate(
                new BigDecimal("1000000"),
                realizedTrades,
                openPositions,
                Map.of(
                        FIRST_ASSET_ID, new BigDecimal("60000"),
                        SECOND_ASSET_ID, new BigDecimal("120000")
                )
        );

        assertThat(contributions).hasSize(2);
        assertThat(contributions.get(0).assetId()).isEqualTo(FIRST_ASSET_ID);
        assertThat(contributions.get(0).profitLoss())
                .isEqualByComparingTo("10000.00000000");
        assertThat(contributions.get(0).contributionRate())
                .isEqualByComparingTo("0.01000000");
        assertThat(contributions.get(1).assetId()).isEqualTo(SECOND_ASSET_ID);
        assertThat(contributions.get(1).profitLoss())
                .isEqualByComparingTo("39950.00000000");
        assertThat(contributions.get(1).contributionRate())
                .isEqualByComparingTo("0.03995000");
    }

    @DisplayName("거래와 포지션이 없으면 종목 기여도도 비어 있다")
    @Test
    void returnEmptyContributions() {
        assertThat(calculator.calculate(
                BigDecimal.ONE,
                List.of(),
                List.of(),
                Map.of()
        )).isEmpty();
    }

    @DisplayName("초기자본은 0보다 커야 한다")
    @Test
    void rejectNonPositiveInitialCapital() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO,
                        List.of(),
                        List.of(),
                        Map.of()
                ));
    }

    @DisplayName("실현 거래 목록에 null이 포함될 수 없다")
    @Test
    void rejectNullRealizedTrade() {
        List<RealizedTradeResult> realizedTrades = new ArrayList<>();
        realizedTrades.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        realizedTrades,
                        List.of(),
                        Map.of()
                ));
    }

    @DisplayName("미청산 포지션에는 종료가격이 필요하다")
    @Test
    void rejectMissingFinalPrice() {
        BacktestPosition position = new BacktestPosition(
                FIRST_ASSET_ID,
                PositionSide.LONG,
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        List.of(),
                        List.of(position),
                        Map.of()
                ));
    }

    @DisplayName("종목 기여도는 롱 포지션만 지원한다")
    @Test
    void rejectShortPosition() {
        BacktestPosition position = new BacktestPosition(
                FIRST_ASSET_ID,
                PositionSide.SHORT,
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ONE,
                        List.of(),
                        List.of(position),
                        Map.of(FIRST_ASSET_ID, BigDecimal.ONE)
                ));
    }
}
