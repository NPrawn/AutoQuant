package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BacktestTradeTest {

    private static final Instant SIGNAL_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant FILLED_AT = Instant.parse("2026-01-02T01:00:00Z");

    @DisplayName("유효한 가상 체결 내역을 생성한다")
    @Test
    void createBacktestTrade() {
        BacktestTrade trade = createTrade(
                FILLED_AT,
                BigDecimal.ONE,
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(5)
        );

        assertThat(trade.getPositionSide()).isEqualTo(PositionSide.LONG);
        assertThat(trade.getSide()).isEqualTo(TradeSide.BUY);
        assertThat(trade.getSignalAt()).isEqualTo(SIGNAL_AT);
        assertThat(trade.getFilledAt()).isEqualTo(FILLED_AT);
        assertThat(trade.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(trade.getReason()).isEqualTo("리밸런싱 매수");
    }

    @DisplayName("체결 시각은 신호 시각보다 이후여야 한다")
    @Test
    void rejectFillAtSignalTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createTrade(
                        SIGNAL_AT,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(5)
                ));
    }

    @DisplayName("체결 수량은 0보다 커야 한다")
    @Test
    void rejectZeroQuantity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createTrade(
                        FILLED_AT,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(5)
                ));
    }

    @DisplayName("수수료 금액은 음수일 수 없다")
    @Test
    void rejectNegativeFeeAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> createTrade(
                        FILLED_AT,
                        BigDecimal.ONE,
                        BigDecimal.valueOf(-1),
                        BigDecimal.valueOf(5)
                ));
    }

    private BacktestTrade createTrade(
            Instant filledAt,
            BigDecimal quantity,
            BigDecimal feeAmount,
            BigDecimal slippageAmount
    ) {
        return new BacktestTrade(
                backtestRun(),
                asset(),
                PositionSide.LONG,
                TradeSide.BUY,
                SIGNAL_AT,
                filledAt,
                BigDecimal.valueOf(100_000),
                quantity,
                BigDecimal.valueOf(100_000),
                feeAmount,
                slippageAmount,
                "  리밸런싱 매수  "
        );
    }

    private BacktestRun backtestRun() {
        Member member = member();
        Strategy strategy = new Strategy(member, "RSI 전략", null, strategyDsl());
        return new BacktestRun(
                member,
                strategy,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                Timeframe.ONE_HOUR,
                "KRW",
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(5),
                RebalancePeriod.WEEKLY,
                ExecutionPriceRule.NEXT_CANDLE_OPEN,
                "engine-v1",
                "market-v1",
                Instant.parse("2025-12-31T23:59:00Z")
        );
    }

    private Asset asset() {
        return new Asset(
                Exchange.UPBIT,
                "KRW-BTC",
                "BTC",
                "KRW",
                "비트코인",
                "Bitcoin",
                Instant.parse("2017-09-25T00:00:00Z")
        );
    }

    private Member member() {
        return new Member(
                LoginProvider.SSAFY,
                "backtest-trade-member",
                "backtest-trade@example.com",
                "백테스트 사용자"
        );
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }
}
