package com.ssafy.a107.trading.backtest.engine;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.performance.CostComparison;
import com.ssafy.a107.trading.backtest.engine.performance.EquitySnapshot;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.engine.performance.TradeStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BacktestExecutionResultTest {

    @DisplayName("실행 결과의 체결과 자산 목록은 외부 변경과 분리한다")
    @Test
    void copyMutableCollections() {
        List<VirtualFill> fills = new ArrayList<>();
        Map<UUID, Asset> assets = new HashMap<>();

        BacktestExecutionResult result = new BacktestExecutionResult(
                performanceReport(),
                fills,
                assets
        );

        fills.add(null);
        assets.put(UUID.randomUUID(), null);

        assertThat(result.fills()).isEmpty();
        assertThat(result.assets()).isEmpty();
        assertThatThrownBy(() -> result.fills().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.assets().put(UUID.randomUUID(), null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("성과 보고서 없이 실행 결과를 만들 수 없다")
    @Test
    void rejectNullPerformanceReport() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BacktestExecutionResult(
                        null,
                        List.of(),
                        Map.of()
                ));
    }

    private PerformanceReport performanceReport() {
        return new PerformanceReport(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new TradeStatistics(0, BigDecimal.ZERO, BigDecimal.ZERO),
                new CostComparison(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ),
                List.of(),
                List.of(new EquitySnapshot(
                        Instant.parse("2026-09-08T00:00:00Z"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ))
        );
    }
}
