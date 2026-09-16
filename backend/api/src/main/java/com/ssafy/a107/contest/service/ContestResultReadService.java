package com.ssafy.a107.contest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.domain.ContestDisqualificationReason;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestEvaluation;
import com.ssafy.a107.contest.domain.ContestPhase;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestEvaluationRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 채점 진행과 결과를 읽는다. 결과와 데이터셋은 회차가 끝난 뒤에만 공개한다 (ADR-0010, 대회 기능 설계 8절).
 */
@Service
@Transactional(readOnly = true)
public class ContestResultReadService {

    private final ContestRoundRepository contestRoundRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestEvaluationRepository contestEvaluationRepository;
    private final ContestResultRepository contestResultRepository;
    private final MarketDatasetRepository marketDatasetRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final BacktestResultRepository backtestResultRepository;

    public ContestResultReadService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository,
            ContestEvaluationRepository contestEvaluationRepository,
            ContestResultRepository contestResultRepository,
            MarketDatasetRepository marketDatasetRepository,
            BacktestRunRepository backtestRunRepository,
            BacktestResultRepository backtestResultRepository
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.contestEvaluationRepository = contestEvaluationRepository;
        this.contestResultRepository = contestResultRepository;
        this.marketDatasetRepository = marketDatasetRepository;
        this.backtestRunRepository = backtestRunRepository;
        this.backtestResultRepository = backtestResultRepository;
    }

    public ContestProgress getProgress(UUID roundId) {
        ContestRound round = round(roundId);
        List<ContestEvaluation> evaluations = contestEvaluationRepository.findAllByRoundId(roundId);

        long finished = evaluations.stream()
                .map(evaluation -> backtestRunRepository.findByBacktestId(evaluation.getBacktestId())
                        .map(BacktestRun::getStatus)
                        .orElse(null))
                .filter(status -> status == BacktestRunStatus.COMPLETED || status == BacktestRunStatus.FAILED)
                .count();

        return new ContestProgress(round.displayStatus(Instant.now()), finished, evaluations.size());
    }

    public List<ContestRanking> getResults(UUID roundId) {
        ContestRound round = requireFinished(roundId);
        Map<UUID, ContestEntry> entries = entriesById(round.getRoundId());

        List<ContestRanking> rankings = new ArrayList<>();
        for (ContestResult result : contestResultRepository.findAllByRoundId(roundId)) {
            ContestEntry entry = entries.get(result.getEntryId());
            rankings.add(new ContestRanking(
                    result.getRanking(),
                    entry.getMember().getDisplayName(),
                    entry.getStrategy().getName(),
                    result.getTotalScore(),
                    result.getReturnScore(),
                    result.getStabilityScore(),
                    result.getDisqualificationReason(),
                    result.getDisqualifiedKind()
            ));
        }
        rankings.sort(Comparator.comparing(
                ContestRanking::ranking,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        return List.copyOf(rankings);
    }

    public ContestMyResult getMyResult(UUID memberId, UUID roundId) {
        ContestRound round = requireFinished(roundId);
        ContestEntry entry = contestEntryRepository.findByRoundRoundIdAndMemberMemberId(roundId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ENTRY_NOT_FOUND));
        ContestResult result = contestResultRepository.findByEntryId(entry.getEntryId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ENTRY_NOT_FOUND));

        Map<UUID, MarketDataset> datasets = new LinkedHashMap<>();
        for (MarketDataset dataset : marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(round.getRoundId())) {
            datasets.put(dataset.getDatasetId(), dataset);
        }

        List<ContestDatasetOutcome> outcomes = new ArrayList<>();
        for (ContestEvaluation evaluation : contestEvaluationRepository.findAllByIdEntryIdIn(
                List.of(entry.getEntryId()))) {
            MarketDataset dataset = datasets.get(evaluation.getId().getDatasetId());
            BacktestResult backtestResult = backtestResultRepository.findById(evaluation.getBacktestId()).orElse(null);
            outcomes.add(new ContestDatasetOutcome(
                    dataset.getKind(),
                    dataset.getPeriodStart(),
                    dataset.getPeriodEnd(),
                    dataset.getEvaluationReturn(),
                    backtestResult == null ? null : backtestResult.getCumulativeReturn(),
                    backtestResult == null ? null : backtestResult.getMdd(),
                    evaluation.getAverageExposure(),
                    backtestResult == null ? null : backtestResult.getTradeCount()
            ));
        }
        outcomes.sort(Comparator.comparing(ContestDatasetOutcome::kind));

        List<ContestResult> results = contestResultRepository.findAllByRoundId(roundId);
        BigDecimal topScore = results.stream()
                .filter(row -> !row.isDisqualified())
                .map(ContestResult::getTotalScore)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new ContestMyResult(result, outcomes, results.size(), topScore);
    }

    public List<MarketDataset> getDatasets(UUID roundId) {
        ContestRound round = requireFinished(roundId);
        return marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(round.getRoundId());
    }

    private Map<UUID, ContestEntry> entriesById(UUID roundId) {
        Map<UUID, ContestEntry> entries = new LinkedHashMap<>();
        for (ContestEntry entry : contestEntryRepository.findAllByRoundRoundId(roundId)) {
            entries.put(entry.getEntryId(), entry);
        }
        return entries;
    }

    private ContestRound round(UUID roundId) {
        return contestRoundRepository.findById(roundId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ROUND_NOT_FOUND));
    }

    private ContestRound requireFinished(UUID roundId) {
        ContestRound round = round(roundId);
        if (round.getPhase() != ContestPhase.FINISHED) {
            throw new ApiException(ApiErrorCode.CONTEST_RESULT_NOT_PUBLISHED);
        }
        return round;
    }

    public record ContestProgress(
            com.ssafy.a107.contest.domain.ContestDisplayStatus status,
            long finishedBacktests,
            long totalBacktests
    ) {
    }

    public record ContestRanking(
            Integer ranking,
            String nickname,
            String strategyName,
            BigDecimal totalScore,
            BigDecimal returnScore,
            BigDecimal stabilityScore,
            ContestDisqualificationReason disqualificationReason,
            MarketDatasetKind disqualifiedKind
    ) {
    }

    public record ContestDatasetOutcome(
            MarketDatasetKind kind,
            Instant periodStart,
            Instant periodEnd,
            BigDecimal marketReturn,
            BigDecimal cumulativeReturn,
            BigDecimal maximumDrawdown,
            BigDecimal averageExposure,
            Integer tradeCount
    ) {
    }

    public record ContestMyResult(
            ContestResult result,
            List<ContestDatasetOutcome> datasets,
            int entryCount,
            BigDecimal topTotalScore
    ) {
    }
}
