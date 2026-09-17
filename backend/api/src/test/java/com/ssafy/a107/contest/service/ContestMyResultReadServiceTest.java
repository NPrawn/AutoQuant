package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestEquityPoint;
import com.ssafy.a107.contest.domain.ContestEvaluation;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestEvaluationRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestEquityId;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/*
 * 내 결과의 장세별 자산 곡선은 마감 뒤에만 싣는다 (ADR-0010).
 */
class ContestMyResultReadServiceTest {

    private static final UUID ROUND_ID = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID ENTRY_ID = UUID.fromString("9a3f4c1e-2b7d-4e8f-a6c5-1d2e3f4a5b6c");
    private static final UUID DATASET_ID = UUID.fromString("5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f");
    private static final UUID BACKTEST_ID = UUID.fromString("1e2d3c4b-5a69-4788-9aab-bccddeeff001");

    private final ContestRoundRepository contestRoundRepository = mock(ContestRoundRepository.class);
    private final ContestEntryRepository contestEntryRepository = mock(ContestEntryRepository.class);
    private final ContestEvaluationRepository contestEvaluationRepository = mock(ContestEvaluationRepository.class);
    private final MarketDatasetRepository marketDatasetRepository = mock(MarketDatasetRepository.class);
    private final BacktestRunRepository backtestRunRepository = mock(BacktestRunRepository.class);
    private final BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
    private final BacktestEquityRepository backtestEquityRepository = mock(BacktestEquityRepository.class);
    private final ContestResultReadService service = new ContestResultReadService(
            contestRoundRepository,
            contestEntryRepository,
            contestEvaluationRepository,
            mock(ContestResultRepository.class),
            marketDatasetRepository,
            backtestRunRepository,
            backtestResultRepository,
            backtestEquityRepository
    );

    private ContestRound round;

    @BeforeEach
    void setUp() {
        round = new ContestRound(
                "1회차 모의 대회",
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-09-20T15:00:00Z"),
                5,
                30,
                13
        );
        ReflectionTestUtils.setField(round, "roundId", ROUND_ID);
        given(contestRoundRepository.findById(ROUND_ID)).willAnswer(invocation -> Optional.of(round));

        ContestEntry entry = mock(ContestEntry.class);
        given(entry.getEntryId()).willReturn(ENTRY_ID);
        given(entry.getStrategy()).willReturn(mock(Strategy.class));
        given(contestEntryRepository.findByRoundRoundIdAndMemberMemberId(ROUND_ID, MEMBER_ID))
                .willReturn(Optional.of(entry));

        MarketDataset dataset = new MarketDataset(
                round,
                MarketDatasetKind.BULL,
                Instant.parse("2030-08-05T00:00:00Z"),
                Instant.parse("2030-11-04T00:00:00Z"),
                new BigDecimal("0.2095"),
                new BigDecimal("0.365"),
                new BigDecimal("0.357"),
                new BigDecimal("0.354"),
                Instant.parse("2019-10-07T00:00:00Z"),
                Instant.parse("2021-10-04T00:00:00Z"),
                20260915L,
                "[]",
                "contest-2026-09-16",
                0
        );
        ReflectionTestUtils.setField(dataset, "datasetId", DATASET_ID);
        given(marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(ROUND_ID)).willReturn(List.of(dataset));
        given(contestEvaluationRepository.findAllByIdEntryIdIn(List.of(ENTRY_ID)))
                .willReturn(List.of(new ContestEvaluation(ENTRY_ID, DATASET_ID, BACKTEST_ID)));

        BacktestRun backtestRun = mock(BacktestRun.class);
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.COMPLETED);
        given(backtestRun.getTimeframe()).willReturn(Timeframe.ONE_HOUR);
        given(backtestRunRepository.findByBacktestId(BACKTEST_ID)).willReturn(Optional.of(backtestRun));
        given(backtestResultRepository.findById(BACKTEST_ID)).willReturn(Optional.of(mock(BacktestResult.class)));
        /* 목 안에서 목을 만들면 스터빙이 끝나지 않은 것으로 잡혀 먼저 만든다. */
        List<BacktestEquity> hourly = List.of(
                equity("2030-08-05T23:00:00Z", "0.010"),
                equity("2030-08-06T00:00:00Z", "0.020"),
                equity("2030-08-06T01:00:00Z", "0.030")
        );
        given(backtestEquityRepository.findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(BACKTEST_ID))
                .willReturn(hourly);
    }

    @DisplayName("종료된 회차는 데이터셋마다 하루 한 점 자산 곡선을 싣는다")
    @Test
    void attachDailyEquityCurveAfterFinish() {
        round.startScoring(Instant.parse("2026-09-20T15:00:00Z"));
        round.finish(Instant.parse("2026-09-20T15:10:00Z"));

        ContestResultReadService.ContestMyResult result = service.getMyResult(MEMBER_ID, ROUND_ID);

        assertThat(result.datasets()).singleElement().satisfies(outcome -> assertThat(outcome.equityCurve())
                .containsExactly(
                        new ContestEquityPoint(Instant.parse("2030-08-06T00:00:00Z"), new BigDecimal("0.020")),
                        new ContestEquityPoint(Instant.parse("2030-08-06T01:00:00Z"), new BigDecimal("0.030"))
                ));
    }

    @DisplayName("제출 중인 회차는 자산 곡선을 싣지 않고 조회도 하지 않는다")
    @Test
    void hideEquityCurveBeforeFinish() {
        ContestResultReadService.ContestMyResult result = service.getMyResult(MEMBER_ID, ROUND_ID);

        assertThat(result.datasets()).singleElement()
                .satisfies(outcome -> assertThat(outcome.equityCurve()).isEmpty());
        then(backtestEquityRepository).should(never()).findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(BACKTEST_ID);
    }

    private static BacktestEquity equity(String valuationAt, String returnRate) {
        BacktestEquity equity = mock(BacktestEquity.class);
        BacktestEquityId id = mock(BacktestEquityId.class);
        given(id.getValuationAt()).willReturn(Instant.parse(valuationAt));
        given(equity.getId()).willReturn(id);
        given(equity.getReturnRate()).willReturn(new BigDecimal(returnRate));
        return equity;
    }
}
