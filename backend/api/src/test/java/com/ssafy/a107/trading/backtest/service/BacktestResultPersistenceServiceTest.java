package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAsset;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAssetId;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualFill;
import com.ssafy.a107.trading.backtest.engine.execution.VirtualOrder;
import com.ssafy.a107.trading.backtest.engine.performance.CostComparison;
import com.ssafy.a107.trading.backtest.engine.performance.EquitySnapshot;
import com.ssafy.a107.trading.backtest.engine.performance.PerformanceReport;
import com.ssafy.a107.trading.backtest.engine.performance.TradeStatistics;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestTradeRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestUniverseAssetBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BacktestResultPersistenceServiceTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ASSET_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private BacktestTradeRepository backtestTradeRepository;

    @Mock
    private BacktestEquityRepository backtestEquityRepository;

    @Mock
    private BacktestUniverseAssetBatchRepository backtestUniverseAssetBatchRepository;

    @Mock
    private BacktestRun backtestRun;

    @Mock
    private Asset asset;

    @Captor
    private ArgumentCaptor<BacktestResult> resultCaptor;

    @Captor
    private ArgumentCaptor<List<BacktestTrade>> tradesCaptor;

    @Captor
    private ArgumentCaptor<List<BacktestEquity>> equitiesCaptor;

    @Captor
    private ArgumentCaptor<List<BacktestUniverseAsset>> universeAssetsCaptor;

    @DisplayName("완료된 백테스트의 성과와 체결 및 자산 스냅샷을 함께 저장한다")
    @Test
    void persistBacktestResult() {
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.COMPLETED);
        given(backtestRun.getBacktestId()).willReturn(BACKTEST_ID);
        given(backtestResultRepository.save(any(BacktestResult.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BacktestResult result = service().persist(
                backtestRun,
                report(),
                List.of(fill()),
                Map.of(ASSET_ID, asset),
                List.of(universeAsset())
        );

        then(backtestResultRepository).should().save(resultCaptor.capture());
        then(backtestTradeRepository).should().saveAll(tradesCaptor.capture());
        then(backtestEquityRepository).should().saveAll(equitiesCaptor.capture());
        then(backtestUniverseAssetBatchRepository).should().insertAll(
                universeAssetsCaptor.capture()
        );

        assertThat(result).isSameAs(resultCaptor.getValue());
        assertThat(resultCaptor.getValue().getFinalAssetValue())
                .isEqualByComparingTo("1100000");
        assertThat(resultCaptor.getValue().getTradeCount()).isEqualTo(1);
        assertThat(resultCaptor.getValue().getBenchmarkReturn())
                .isEqualByComparingTo("0.08");
        assertThat(tradesCaptor.getValue()).singleElement()
                .satisfies(trade -> {
                    assertThat(trade.getAsset()).isSameAs(asset);
                    assertThat(trade.getFilledAt())
                            .isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
                    assertThat(trade.getFeeAmount())
                            .isEqualByComparingTo("100");
                });
        assertThat(equitiesCaptor.getValue()).singleElement()
                .satisfies(equity -> {
                    assertThat(equity.getId().getBacktestId())
                            .isEqualTo(BACKTEST_ID);
                    assertThat(equity.getTotalAssetValue())
                            .isEqualByComparingTo("1100000");
                });
        assertThat(universeAssetsCaptor.getValue()).singleElement()
                .satisfies(universeAsset -> {
                    assertThat(universeAsset.getAsset()).isSameAs(asset);
                    assertThat(universeAsset.isSelected()).isTrue();
                });
    }

    @DisplayName("체결 자산이 누락되면 결과를 저장하지 않는다")
    @Test
    void rejectMissingAssetBeforePersisting() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service().persist(
                        backtestRun,
                        report(),
                        List.of(fill()),
                        Map.of()
                ));

        then(backtestResultRepository).shouldHaveNoInteractions();
        then(backtestTradeRepository).shouldHaveNoInteractions();
        then(backtestEquityRepository).shouldHaveNoInteractions();
    }

    private BacktestResultPersistenceService service() {
        return new BacktestResultPersistenceService(
                backtestResultRepository,
                backtestTradeRepository,
                backtestEquityRepository,
                backtestUniverseAssetBatchRepository
        );
    }

    private BacktestUniverseAsset universeAsset() {
        return new BacktestUniverseAsset(
                new BacktestUniverseAssetId(
                        BACKTEST_ID,
                        Instant.parse("2026-01-02T00:00:00Z"),
                        ASSET_ID
                ),
                backtestRun,
                asset,
                (short) 1,
                true
        );
    }

    private PerformanceReport report() {
        EquitySnapshot snapshot = new EquitySnapshot(
                Instant.parse("2026-01-02T00:00:00Z"),
                new BigDecimal("1100000"),
                new BigDecimal("1100000"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO
        );

        return new PerformanceReport(
                new BigDecimal("1100000"),
                new BigDecimal("0.1"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                Optional.of(new BigDecimal("1.2")),
                Optional.of(new BigDecimal("0.2")),
                Optional.of(BigDecimal.ONE),
                Optional.of(new BigDecimal("0.08")),
                Optional.of(new BigDecimal("0.02")),
                new TradeStatistics(
                        1,
                        new BigDecimal("100"),
                        new BigDecimal("200")
                ),
                new CostComparison(
                        new BigDecimal("1100300"),
                        new BigDecimal("1100000"),
                        new BigDecimal("100"),
                        new BigDecimal("200"),
                        new BigDecimal("0.1003"),
                        new BigDecimal("0.1"),
                        new BigDecimal("0.0003")
                ),
                List.of(),
                List.of(snapshot)
        );
    }

    private VirtualFill fill() {
        return new VirtualFill(
                new VirtualOrder(
                        ASSET_ID,
                        PositionSide.LONG,
                        TradeSide.BUY,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        new BigDecimal("100000"),
                        new BigDecimal("100000")
                ),
                new BigDecimal("100000"),
                BigDecimal.ONE,
                new BigDecimal("100000"),
                new BigDecimal("100"),
                new BigDecimal("200")
        );
    }
}
