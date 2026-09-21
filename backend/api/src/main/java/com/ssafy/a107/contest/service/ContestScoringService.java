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
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * 회차를 채점한다 (대회 기능 설계 7절).
 *
 * 점수가 절대값이라 제출의 백테스트 4벌이 끝나는 대로 점수를 낸다. 순위는 그때까지 채점된 제출 사이의
 * 자리라 참가자가 늘면 바뀌고, 마감 때 그대로 확정된다.
 *
 * 1. 제출하면 ContestEvaluationService 가 백테스트 4벌을 만든다. 마감이 지난 회차에서 평가가 빠진 제출은
 *    채점 시작이 채운다.
 * 2. 끝난 제출이 생길 때마다 회차 결과를 다시 쓴다.
 * 3. 마감이 지나고 모든 제출이 채점되면 회차를 종료로 바꾼다.
 */
@Service
public class ContestScoringService {

    private static final Logger log = LoggerFactory.getLogger(ContestScoringService.class);

    private final ContestRoundRepository contestRoundRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final MarketDatasetRepository marketDatasetRepository;
    private final ContestEvaluationRepository contestEvaluationRepository;
    private final ContestResultRepository contestResultRepository;
    private final ContestEvaluationService contestEvaluationService;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestEquityRepository backtestEquityRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ContestScoringService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository,
            MarketDatasetRepository marketDatasetRepository,
            ContestEvaluationRepository contestEvaluationRepository,
            ContestResultRepository contestResultRepository,
            ContestEvaluationService contestEvaluationService,
            BacktestRunRepository backtestRunRepository,
            BacktestResultRepository backtestResultRepository,
            BacktestEquityRepository backtestEquityRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.marketDatasetRepository = marketDatasetRepository;
        this.contestEvaluationRepository = contestEvaluationRepository;
        this.contestResultRepository = contestResultRepository;
        this.contestEvaluationService = contestEvaluationService;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestResultRepository = backtestResultRepository;
        this.backtestEquityRepository = backtestEquityRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<UUID> findRoundsToStart(Instant now) {
        return contestRoundRepository
                .findAllByPhaseAndSubmissionEndsAtLessThanEqual(ContestPhase.READY, now)
                .stream()
                .map(ContestRound::getRoundId)
                .toList();
    }

    /** 제출을 받는 중이거나 채점 중인 회차. 끝난 백테스트가 있으면 그때그때 점수를 낸다. */
    @Transactional(readOnly = true)
    public List<UUID> findRoundsAcceptingScores() {
        return Stream.concat(
                        contestRoundRepository.findAllByPhase(ContestPhase.READY).stream(),
                        contestRoundRepository.findAllByPhase(ContestPhase.SCORING).stream())
                .map(ContestRound::getRoundId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> findRoundsInScoring() {
        return contestRoundRepository.findAllByPhase(ContestPhase.SCORING).stream()
                .map(ContestRound::getRoundId)
                .toList();
    }

    /** 마감이 지난 회차를 채점 중으로 바꾸고, 아직 돌리지 않은 제출만 돌린다. 제출이 없으면 바로 종료한다. */
    @Transactional
    public void startScoring(UUID roundId, Instant now) {
        ContestRound round = contestRoundRepository.findById(roundId).orElseThrow();
        if (round.getPhase() != ContestPhase.READY) {
            return;
        }

        round.startScoring(now);
        List<ContestEntry> entries = contestEntryRepository.findAllByRoundRoundId(roundId);
        if (entries.isEmpty()) {
            round.finish(now);
            log.info("대회 회차 {} 는 제출이 없어 바로 종료한다", roundId);
            return;
        }

        Set<UUID> evaluated = contestEvaluationRepository.findAllByRoundId(roundId).stream()
                .map(evaluation -> evaluation.getId().getEntryId())
                .collect(Collectors.toSet());

        int started = 0;
        for (ContestEntry entry : entries) {
            if (evaluated.contains(entry.getEntryId())) {
                continue;
            }
            contestEvaluationService.evaluate(entry);
            started++;
        }
        log.info("대회 회차 {} 채점 시작: 제출 {}건 중 {}건을 새로 돌린다", roundId, entries.size(), started);
    }

    /*
     * 백테스트가 끝난 제출의 점수를 저장한다. 점수는 절대값이라 제출별로 확정되지만 순위는 참가자가 늘면
     * 바뀌므로 회차 결과를 통째로 다시 쓴다. 아직 도는 제출은 다음 차례에 들어온다.
     */
    @Transactional
    public int scoreSettledEntries(UUID roundId) {
        Map<UUID, MarketDataset> datasets = datasets(roundId);
        Set<UUID> alreadyScored = contestResultRepository.findAllByRoundId(roundId).stream()
                .map(ContestResult::getEntryId)
                .collect(Collectors.toSet());
        Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> settled = new LinkedHashMap<>();
        Set<UUID> running = new LinkedHashSet<>();

        for (ContestEvaluation evaluation : contestEvaluationRepository.findAllByRoundId(roundId)) {
            UUID entryId = evaluation.getId().getEntryId();
            BacktestRun backtestRun = backtestRunRepository.findByBacktestId(evaluation.getBacktestId()).orElseThrow();
            if (backtestRun.getStatus() != BacktestRunStatus.COMPLETED
                    && backtestRun.getStatus() != BacktestRunStatus.FAILED) {
                running.add(entryId);
                continue;
            }
            MarketDataset dataset = datasets.get(evaluation.getId().getDatasetId());
            settled.computeIfAbsent(entryId, key -> new LinkedHashMap<>())
                    .put(dataset.getKind(), outcome(evaluation, backtestRun, dataset));
        }

        running.forEach(settled::remove);
        contestResultRepository.deleteAllByRoundId(roundId);
        if (settled.isEmpty()) {
            return 0;
        }

        List<ContestResult> results = ContestScoreCalculator.score(settled, generalMarketReturn(roundId));
        contestResultRepository.saveAll(results);
        notifyScored(roundId, results.stream()
                .map(ContestResult::getEntryId)
                .filter(entryId -> !alreadyScored.contains(entryId))
                .collect(Collectors.toSet()));
        return results.size();
    }

    /*
     * 이번에 처음 채점된 제출에만 알린다. 회차 결과는 순위 때문에 채점할 때마다 통째로 다시 쓰지만,
     * 이미 알린 제출까지 다시 알리면 같은 알림이 쌓인다. 교체 제출은 이전 결과를 버려서 다시 알린다.
     */
    private void notifyScored(UUID roundId, Set<UUID> scoredEntryIds) {
        if (scoredEntryIds.isEmpty()) {
            return;
        }
        for (ContestEntry entry : contestEntryRepository.findAllByRoundRoundId(roundId)) {
            if (!scoredEntryIds.contains(entry.getEntryId())) {
                continue;
            }
            applicationEventPublisher.publishEvent(new NotificationRequestedEvent(
                    entry.getMember().getMemberId(),
                    NotificationType.CONTEST_SCORED,
                    ContestNotificationMessages.scoredTitle(entry.getRound()),
                    ContestNotificationMessages.scoredBody(),
                    roundId.toString()
            ));
        }
    }

    /** 일반장 데이터셋의 균등 보유 수익률. 수익률 점수를 재는 기준이다 (ADR-0003). */
    private BigDecimal generalMarketReturn(UUID roundId) {
        return marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(roundId).stream()
                .filter(dataset -> dataset.getKind() == MarketDatasetKind.GENERAL)
                .map(MarketDataset::getEvaluationReturn)
                .findFirst()
                .orElseThrow();
    }

    /** 모든 제출이 채점됐으면 회차를 종료한다. 아직 도는 제출이 있으면 기다린다. */
    @Transactional
    public boolean finishScoring(UUID roundId, Instant now) {
        ContestRound round = contestRoundRepository.findById(roundId).orElseThrow();
        if (round.getPhase() != ContestPhase.SCORING) {
            return false;
        }

        for (ContestEvaluation evaluation : contestEvaluationRepository.findAllByRoundId(roundId)) {
            BacktestRun backtestRun = backtestRunRepository.findByBacktestId(evaluation.getBacktestId()).orElseThrow();
            if (backtestRun.getStatus() != BacktestRunStatus.COMPLETED
                    && backtestRun.getStatus() != BacktestRunStatus.FAILED) {
                return false;
            }
        }

        int results = scoreSettledEntries(roundId);
        /*
         * 위 채점이 결과를 통째로 지우면서 clearAutomatically 로 영속성 컨텍스트를 비운다. 처음 읽은 round 는
         * 준영속이라 여기서 고쳐도 커밋되지 않는다. 다시 읽어 종료시켜야 회차가 SCORING 에 머무르지 않는다.
         */
        ContestRound settledRound = contestRoundRepository.findById(roundId).orElseThrow();
        settledRound.finish(now);
        log.info("대회 회차 {} 채점 완료: 결과 {}건", roundId, results);
        return true;
    }

    private ContestEvaluationOutcome outcome(
            ContestEvaluation evaluation,
            BacktestRun backtestRun,
            MarketDataset dataset
    ) {
        if (backtestRun.getStatus() == BacktestRunStatus.FAILED) {
            return ContestEvaluationOutcome.failed();
        }

        return backtestResultRepository.findById(evaluation.getBacktestId())
                .map(result -> {
                    BigDecimal exposure = backtestEquityRepository
                            .findAverageExposure(evaluation.getBacktestId())
                            .orElse(BigDecimal.ZERO);
                    evaluation.recordAverageExposure(exposure);
                    return ContestEvaluationOutcome.completed(
                            result.getCumulativeReturn(),
                            result.getMdd(),
                            exposure,
                            dataset.getMarketMaximumDrawdown(backtestRun.getTimeframe())
                    );
                })
                .orElseGet(ContestEvaluationOutcome::failed);
    }

    private Map<UUID, MarketDataset> datasets(UUID roundId) {
        Map<UUID, MarketDataset> datasets = new LinkedHashMap<>();
        for (MarketDataset dataset : marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(roundId)) {
            datasets.put(dataset.getDatasetId(), dataset);
        }
        return datasets;
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
