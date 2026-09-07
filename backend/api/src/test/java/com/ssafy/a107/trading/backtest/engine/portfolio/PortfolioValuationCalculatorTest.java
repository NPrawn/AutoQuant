package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PortfolioValuationCalculatorTest {

    private static final UUID BITCOIN_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ETHEREUM_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );

    private final PortfolioValuationCalculator calculator =
            new PortfolioValuationCalculator();

    @DisplayName("포지션이 없으면 현금 잔액을 총 평가자산으로 반환한다")
    @Test
    void valueCashOnlyPortfolio() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        BigDecimal totalAssetValue = calculator.calculateTotalAssetValue(
                portfolio,
                Map.of()
        );

        assertThat(totalAssetValue).isEqualByComparingTo("1000000.00000000");
        assertThat(totalAssetValue.scale()).isEqualTo(8);
    }

    @DisplayName("현금과 복수 포지션의 현재 평가금액을 합산한다")
    @Test
    void valuePortfolioWithPositions() {
        BacktestPortfolio portfolio = portfolioWithPositions();
        Map<UUID, BigDecimal> currentPrices = Map.of(
                BITCOIN_ID, BigDecimal.valueOf(120_000),
                ETHEREUM_ID, BigDecimal.valueOf(40_000)
        );

        BigDecimal totalAssetValue = calculator.calculateTotalAssetValue(
                portfolio,
                currentPrices
        );

        assertThat(totalAssetValue).isEqualByComparingTo("1009825.00000000");
    }

    @DisplayName("보유 자산의 현재 가격이 없으면 평가할 수 없다")
    @Test
    void rejectMissingCurrentPrice() {
        BacktestPortfolio portfolio = portfolioWithPositions();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateTotalAssetValue(
                        portfolio,
                        Map.of(BITCOIN_ID, BigDecimal.valueOf(120_000))
                ));
    }

    @DisplayName("보유 자산의 현재 가격은 0보다 커야 한다")
    @Test
    void rejectZeroCurrentPrice() {
        BacktestPortfolio portfolio = portfolioWithPositions();
        Map<UUID, BigDecimal> currentPrices = Map.of(
                BITCOIN_ID, BigDecimal.ZERO,
                ETHEREUM_ID, BigDecimal.valueOf(40_000)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculateTotalAssetValue(
                        portfolio,
                        currentPrices
                ));
    }

    private BacktestPortfolio portfolioWithPositions() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );
        portfolio.applyBuy(fill(
                BITCOIN_ID,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(100)
        ));
        portfolio.applyBuy(fill(
                ETHEREUM_ID,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(150_000),
                BigDecimal.valueOf(75)
        ));
        return portfolio;
    }

    private VirtualFill fill(
            UUID assetId,
            BigDecimal executedPrice,
            BigDecimal quantity,
            BigDecimal notional,
            BigDecimal feeAmount
    ) {
        VirtualOrder order = new VirtualOrder(
                assetId,
                PositionSide.LONG,
                TradeSide.BUY,
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-02T01:00:00Z"),
                executedPrice,
                notional
        );
        return new VirtualFill(
                order,
                executedPrice,
                quantity,
                notional,
                feeAmount,
                BigDecimal.ZERO
        );
    }
}
