package com.ssafy.a107.trading.backtest.engine.portfolio;

import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class BacktestPortfolioTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );

    @DisplayName("초기 현금으로 빈 백테스트 포트폴리오를 생성한다")
    @Test
    void createEmptyPortfolio() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1000000");
        assertThat(portfolio.getPositions()).isEmpty();
    }

    @DisplayName("보유하지 않은 자산의 포지션은 조회되지 않는다")
    @Test
    void returnEmptyForMissingPosition() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThat(portfolio.findPosition(UUID.randomUUID())).isEmpty();
    }

    @DisplayName("초기 현금은 0보다 커야 한다")
    @Test
    void rejectZeroInitialCash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BacktestPortfolio(BigDecimal.ZERO));
    }

    @DisplayName("신규 매수 체결은 수수료를 포함해 현금을 차감하고 포지션을 생성한다")
    @Test
    void applyNewBuyFill() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        portfolio.applyBuy(fill(
                PositionSide.LONG,
                TradeSide.BUY,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(100)
        ));

        BacktestPosition position = portfolio.findPosition(ASSET_ID).orElseThrow();
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("799900.00000000");
        assertThat(position.quantity()).isEqualByComparingTo("2");
        assertThat(position.averageEntryPrice()).isEqualByComparingTo("100000.00000000");
        assertThat(position.costBasis()).isEqualByComparingTo("200100.00000000");
    }

    @DisplayName("추가 매수 체결은 수량 가중 평균 진입가를 계산한다")
    @Test
    void applyAdditionalBuyFill() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );
        portfolio.applyBuy(fill(
                PositionSide.LONG,
                TradeSide.BUY,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(100)
        ));

        portfolio.applyBuy(fill(
                PositionSide.LONG,
                TradeSide.BUY,
                BigDecimal.valueOf(120_000),
                BigDecimal.ONE,
                BigDecimal.valueOf(120_000),
                BigDecimal.valueOf(60)
        ));

        BacktestPosition position = portfolio.findPosition(ASSET_ID).orElseThrow();
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("679840.00000000");
        assertThat(position.quantity()).isEqualByComparingTo("3");
        assertThat(position.averageEntryPrice()).isEqualByComparingTo("106666.66666667");
        assertThat(position.costBasis()).isEqualByComparingTo("320160.00000000");
    }

    @DisplayName("현금이 부족한 매수 체결은 포트폴리오를 변경하지 않는다")
    @Test
    void rejectBuyFillWhenCashIsInsufficient() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(100_000)
        );
        VirtualFill fill = fill(
                PositionSide.LONG,
                TradeSide.BUY,
                BigDecimal.valueOf(100_000),
                BigDecimal.ONE,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50)
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> portfolio.applyBuy(fill));
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("100000");
        assertThat(portfolio.getPositions()).isEmpty();
    }

    @DisplayName("매수 반영에는 매도 체결을 전달할 수 없다")
    @Test
    void rejectSellFillFromBuyApplication() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolio.applyBuy(fill(
                        PositionSide.LONG,
                        TradeSide.SELL,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(50)
                )));
    }

    @DisplayName("매수 반영은 롱 포지션만 지원한다")
    @Test
    void rejectShortPositionBuyFill() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolio.applyBuy(fill(
                        PositionSide.SHORT,
                        TradeSide.BUY,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(50)
                )));
    }

    @DisplayName("부분 매도 체결은 수수료를 차감한 현금을 더하고 남은 포지션을 유지한다")
    @Test
    void applyPartialSellFill() {
        BacktestPortfolio portfolio = portfolioWithPosition();

        RealizedTradeResult result = portfolio.applySell(fill(
                PositionSide.LONG,
                TradeSide.SELL,
                BigDecimal.valueOf(120_000),
                BigDecimal.ONE,
                BigDecimal.valueOf(120_000),
                BigDecimal.valueOf(60)
        ));

        BacktestPosition position = portfolio.findPosition(ASSET_ID).orElseThrow();
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("919840.00000000");
        assertThat(position.quantity()).isEqualByComparingTo("1");
        assertThat(position.averageEntryPrice()).isEqualByComparingTo("100000.00000000");
        assertThat(position.costBasis()).isEqualByComparingTo("100050.00000000");
        assertThat(result.allocatedCostBasis()).isEqualByComparingTo("100050.00000000");
        assertThat(result.netProceeds()).isEqualByComparingTo("119940.00000000");
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("19890.00000000");
    }

    @DisplayName("부분 매도 후 잔여 원가가 8자리보다 작아도 포지션을 유지한다")
    @Test
    void retainSubMoneyScaleCostBasisAfterPartialSell() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.ZERO,
                List.of(new BacktestPosition(
                        ASSET_ID,
                        PositionSide.LONG,
                        new BigDecimal("0.00000000001"),
                        BigDecimal.valueOf(1_000),
                        new BigDecimal("0.00000001")
                ))
        );

        RealizedTradeResult result = portfolio.applySell(fill(
                PositionSide.LONG,
                TradeSide.SELL,
                BigDecimal.valueOf(1_000),
                new BigDecimal("0.0000000000099"),
                new BigDecimal("0.0000000099"),
                BigDecimal.ZERO
        ));

        BacktestPosition position = portfolio.findPosition(ASSET_ID).orElseThrow();
        assertThat(position.quantity()).isEqualByComparingTo("0.0000000000001");
        assertThat(position.costBasis()).isEqualByComparingTo("0.0000000001");
        assertThat(result.allocatedCostBasis()).isEqualByComparingTo("0.0000000099");
    }

    @DisplayName("전량 매도 체결은 포지션을 제거한다")
    @Test
    void applyFullSellFill() {
        BacktestPortfolio portfolio = portfolioWithPosition();

        RealizedTradeResult result = portfolio.applySell(fill(
                PositionSide.LONG,
                TradeSide.SELL,
                BigDecimal.valueOf(120_000),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(240_000),
                BigDecimal.valueOf(120)
        ));

        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1039780.00000000");
        assertThat(portfolio.findPosition(ASSET_ID)).isEmpty();
        assertThat(result.allocatedCostBasis()).isEqualByComparingTo("200100.00000000");
        assertThat(result.netProceeds()).isEqualByComparingTo("239880.00000000");
        assertThat(result.realizedProfitLoss()).isEqualByComparingTo("39780.00000000");
    }

    @DisplayName("보유하지 않은 자산은 매도할 수 없다")
    @Test
    void rejectSellFillForMissingPosition() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> portfolio.applySell(fill(
                        PositionSide.LONG,
                        TradeSide.SELL,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(50)
                )));
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("1000000");
        assertThat(portfolio.getPositions()).isEmpty();
    }

    @DisplayName("보유 수량을 초과해서 매도할 수 없다")
    @Test
    void rejectSellFillExceedingPositionQuantity() {
        BacktestPortfolio portfolio = portfolioWithPosition();

        assertThatIllegalStateException()
                .isThrownBy(() -> portfolio.applySell(fill(
                        PositionSide.LONG,
                        TradeSide.SELL,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(3),
                        BigDecimal.valueOf(300_000),
                        BigDecimal.valueOf(150)
                )));
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("799900.00000000");
        assertThat(portfolio.findPosition(ASSET_ID).orElseThrow().quantity())
                .isEqualByComparingTo("2");
    }

    @DisplayName("매도 수수료가 체결 금액을 초과할 수 없다")
    @Test
    void rejectSellFeeExceedingNotional() {
        BacktestPortfolio portfolio = portfolioWithPosition();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolio.applySell(fill(
                        PositionSide.LONG,
                        TradeSide.SELL,
                        BigDecimal.valueOf(100),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(101)
                )));
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("799900.00000000");
        assertThat(portfolio.findPosition(ASSET_ID).orElseThrow().quantity())
                .isEqualByComparingTo("2");
    }

    @DisplayName("매도 반영에는 매수 체결을 전달할 수 없다")
    @Test
    void rejectBuyFillFromSellApplication() {
        BacktestPortfolio portfolio = portfolioWithPosition();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolio.applySell(fill(
                        PositionSide.LONG,
                        TradeSide.BUY,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(50)
                )));
    }

    @DisplayName("매도 반영은 롱 포지션만 지원한다")
    @Test
    void rejectShortPositionSellFill() {
        BacktestPortfolio portfolio = portfolioWithPosition();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> portfolio.applySell(fill(
                        PositionSide.SHORT,
                        TradeSide.SELL,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(50)
                )));
    }

    private BacktestPortfolio portfolioWithPosition() {
        BacktestPortfolio portfolio = new BacktestPortfolio(
                BigDecimal.valueOf(1_000_000)
        );
        portfolio.applyBuy(fill(
                PositionSide.LONG,
                TradeSide.BUY,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(100)
        ));
        return portfolio;
    }

    private VirtualFill fill(
            PositionSide positionSide,
            TradeSide side,
            BigDecimal executedPrice,
            BigDecimal quantity,
            BigDecimal notional,
            BigDecimal feeAmount
    ) {
        VirtualOrder order = new VirtualOrder(
                ASSET_ID,
                positionSide,
                side,
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
