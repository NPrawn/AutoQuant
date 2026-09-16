package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestEvaluation;
import com.ssafy.a107.contest.domain.ContestEvaluationOutcome;
import com.ssafy.a107.contest.domain.ContestPhase;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.domain.ContestScoreCalculator;
import com.ssafy.a107.contest.domain.ContestScoringPolicy;
import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestEvaluationRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import com.ssafy.a107.trading.backtest.service.BacktestRunCommandService;
import com.ssafy.a107.trading.backtest.service.CreateContestBacktestRunCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 마감된 회차를 채점한다 (대회 기능 설계 7절).
 *
 * 1. 마감이 지난 제출 중 회차를 채점 중으로 바꾸고, 제출마다 데이터셋 4벌의 대회 백테스트를 만든다.
 * 2. 모든 백테스트가 끝나면(완료 또는 실패) 점수를 계산해 저장하고 회차를 종료로 바꾼다.
 */
@Service
public class ContestScoringService {

    private static final Logger log = LoggerFactory.getLogger(ContestScoringService.class);
    private static final String ENGINE_VERSION = "contest-v1";

    private final ContestRoundRepository contestRoundRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final MarketDatasetRepository marketDatasetRepository;
    private final ContestEvaluationRepository contestEvaluationRepository;
    private final ContestResultRepository contestResultRepository;
    private final BacktestRunCommandService backtestRunCommandService;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestEquityRepository backtestEquityRepository;

    public ContestScoringService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository,
            MarketDatasetRepository marketDatasetRepository,
            ContestEvaluationRepository contestEvaluationRepository,
            ContestResultRepository contestResultRepository,
            BacktestRunCommandService backtestRunCommandService,
            BacktestRunRepository backtestRunRepository,
            BacktestResultRepository backtestResultRepository,
            BacktestEquityRepository backtestEquityRepository
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.marketDatasetRepository = marketDatasetRepository;
        this.contestEvaluationRepository = contestEvaluationRepository;
        this.contestResultRepository = contestResultRepository;
        this.backtestRunCommandService = backtestRunCommandService;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestResultRepository = backtestResultRepository;
        this.backtestEquityRepository = backtestEquityRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> findRoundsToStart(Instant now) {
        return contestRoundRepository
                .findAllByPhaseAndSubmissionEndsAtLessThanEqual(ContestPhase.READY, now)
                .stream()
                .map(ContestRound::getRoundId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> findRoundsInScoring() {
        return contestRoundRepository.findAllByPhase(ContestPhase.SCORING).stream()
                .map(ContestRound::getRoundId)
                .toList();
    }

    /** 제출마다 데이터셋 4벌의 대회 백테스트를 만든다. 제출이 없으면 바로 종료한다. */
    @Transactional
    public void startScoring(UUID roundId, Instant now) {
        ContestRound round = contestRoundRepository.findById(roundId).orElseThrow();
        if (round.getPhase() != ContestPhase.READY) {
            return;
        }

        List<MarketDataset> datasets = marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(roundId);
        if (datasets.size() != MarketDatasetKind.values().length) {
            log.warn("대회 회차 {} 는 데이터셋이 {}벌이라 채점을 시작하지 않는다", roundId, datasets.size());
            return;
        }

        round.startScoring(now);
        List<ContestEntry> entries = contestEntryRepository.findAllByRoundRoundId(roundId);
        if (entries.isEmpty()) {
            round.finish(now);
            log.info("대회 회차 {} 는 제출이 없어 바로 종료한다", roundId);
            return;
        }

        for (ContestEntry entry : entries) {
            for (MarketDataset dataset : datasets) {
                BacktestRun backtestRun = backtestRunCommandService.createContestBacktestRun(
                        new CreateContestBacktestRunCommand(
                                entry.getStrategy().getStrategyId(),
                                dataset.getDatasetId(),
                                dataset.getPeriodStart(),
                                dataset.getPeriodEnd(),
                                ContestScoringPolicy.INITIAL_CAPITAL,
                                ContestScoringPolicy.FEE_RATE,
                                ContestScoringPolicy.SLIPPAGE_BPS,
                                ENGINE_VERSION,
                                dataset.getKind().name() + "-" + roundId
                        )
                );
                contestEvaluationRepository.save(new ContestEvaluation(
                        entry.getEntryId(),
                        dataset.getDatasetId(),
                        backtestRun.getBacktestId()
                ));
            }
        }
        log.info("대회 회차 {} 채점 시작: 제출 {}건 × 데이터셋 {}벌", roundId, entries.size(), datasets.size());
    }

    /** 백테스트가 모두 끝났으면 점수를 저장하고 회차를 종료한다. 아직이면 아무것도 하지 않는다. */
    @Transactional
    public boolean finishScoring(UUID roundId, Instant now) {
        ContestRound round = contestRoundRepository.findById(roundId).orElseThrow();
        if (round.getPhase() != ContestPhase.SCORING) {
            return false;
        }

        List<ContestEvaluation> evaluations = contestEvaluationRepository.findAllByRoundId(roundId);
        Map<UUID, MarketDatasetKind> kinds = datasetKinds(roundId);
        Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes = new LinkedHashMap<>();

        for (ContestEvaluation evaluation : evaluations) {
            BacktestRun backtestRun = backtestRunRepository.findByBacktestId(evaluation.getBacktestId()).orElseThrow();
            if (backtestRun.getStatus() != BacktestRunStatus.COMPLETED
                    && backtestRun.getStatus() != BacktestRunStatus.FAILED) {
                return false;
            }

            MarketDatasetKind kind = kinds.get(evaluation.getId().getDatasetId());
            outcomes.computeIfAbsent(evaluation.getId().getEntryId(), key -> new LinkedHashMap<>())
                    .put(kind, outcome(evaluation, backtestRun));
        }

        List<ContestResult> results = ContestScoreCalculator.score(outcomes);
        contestResultRepository.saveAll(results);
        round.finish(now);
        log.info("대회 회차 {} 채점 완료: 결과 {}건", roundId, results.size());
        return true;
    }

    private ContestEvaluationOutcome outcome(ContestEvaluation evaluation, BacktestRun backtestRun) {
        if (backtestRun.getStatus() == BacktestRunStatus.FAILED) {
            return ContestEvaluationOutcome.failed();
        }

        return backtestResultRepository.findById(evaluation.getBacktestId())
                .map(result -> {
                    BigDecimal exposure = backtestEquityRepository
                            .findAverageExposure(evaluation.getBacktestId())
                            .orElse(BigDecimal.ZERO);
                    evaluation.recordAverageExposure(exposure);
                    return ContestEvaluationOutcome.completed(result.getCumulativeReturn(), result.getMdd(), exposure);
                })
                .orElseGet(ContestEvaluationOutcome::failed);
    }

    private Map<UUID, MarketDatasetKind> datasetKinds(UUID roundId) {
        Map<UUID, MarketDatasetKind> kinds = new LinkedHashMap<>();
        for (MarketDataset dataset : marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(roundId)) {
            kinds.put(dataset.getDatasetId(), dataset.getKind());
        }
        return kinds;
    }

    /** 채점 진행률이다. 화면이 채점 중에 보여 준다. */
    @Transactional(readOnly = true)
    public List<UUID> findFinishedBacktestIds(UUID roundId) {
        List<UUID> finished = new ArrayList<>();
        for (ContestEvaluation evaluation : contestEvaluationRepository.findAllByRoundId(roundId)) {
            BacktestRunStatus status = backtestRunRepository.findByBacktestId(evaluation.getBacktestId())
                    .map(BacktestRun::getStatus)
                    .orElse(null);
            if (status == BacktestRunStatus.COMPLETED || status == BacktestRunStatus.FAILED) {
                finished.add(evaluation.getBacktestId());
            }
        }
        return finished;
    }
}
