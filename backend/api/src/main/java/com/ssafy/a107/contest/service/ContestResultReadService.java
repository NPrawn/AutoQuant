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
import com.ssafy.a107.contest.domain.ContestRankingSort;
import com.ssafy.a107.contest.repository.ContestRankingRow;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * 채점 진행과 결과를 읽는다.
 *
 * 점수가 절대값이라 제출이 채점되는 대로 결과와 순위를 공개한다. 마감 전 순위는 그때까지 채점된 제출
 * 사이의 자리라 잠정이다. 채점용 데이터의 출처(뽑은 주, 시드, 원본 구간)는 마감 뒤에만 공개한다 (ADR-0010).
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
        ContestRound round = round(roundId);
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
        ContestRound round = round(roundId);
        ContestEntry entry = contestEntryRepository.findByRoundRoundIdAndMemberMemberId(roundId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ENTRY_NOT_FOUND));
        /* 백테스트가 도는 동안에는 점수가 없다. 진행 수만 채워 보낸다. */
        ContestResult result = contestResultRepository.findByEntryId(entry.getEntryId()).orElse(null);

        Map<UUID, MarketDataset> datasets = new LinkedHashMap<>();
        for (MarketDataset dataset : marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(round.getRoundId())) {
            datasets.put(dataset.getDatasetId(), dataset);
        }

        List<ContestDatasetOutcome> outcomes = new ArrayList<>();
        int finishedBacktests = 0;
        List<ContestEvaluation> evaluations = contestEvaluationRepository.findAllByIdEntryIdIn(
                List.of(entry.getEntryId()));
        for (ContestEvaluation evaluation : evaluations) {
            MarketDataset dataset = datasets.get(evaluation.getId().getDatasetId());
            BacktestRunStatus status = backtestRunRepository.findByBacktestId(evaluation.getBacktestId())
                    .map(BacktestRun::getStatus)
                    .orElse(null);
            if (status == BacktestRunStatus.COMPLETED || status == BacktestRunStatus.FAILED) {
                finishedBacktests++;
            }
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

        return new ContestMyResult(
                result,
                entry.getStrategy().getStrategyId(),
                entry.getStrategy().getName(),
                outcomes,
                results.size(),
                topScore,
                finishedBacktests,
                evaluations.size()
        );
    }

    /*
     * 랭킹 화면이 쓰는 회차 순위다. 정렬·국면 필터·검색·페이지를 모두 DB 에서 처리한다.
     * 참가가 많은 회차에서 전체를 읽어 화면이 자르면 응답이 그만큼 커진다.
     */
    public ContestRankings getRankings(
            UUID roundId,
            ContestRankingSort sort,
            MarketDatasetKind kind,
            String query,
            Pageable pageable
    ) {
        round(roundId);

        String keyword = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase() + "%";
        Page<ContestRankingRow> rows = contestResultRepository.findRankings(
                roundId,
                keyword,
                kind == MarketDatasetKind.BULL,
                kind == MarketDatasetKind.BEAR,
                kind == MarketDatasetKind.SIDEWAYS,
                sort == ContestRankingSort.STABILITY,
                pageable
        );
        return new ContestRankings(rows, contestResultRepository.countDisqualifiedByRoundId(roundId));
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
            Long strategyId,
            String strategyName,
            List<ContestDatasetOutcome> datasets,
            int entryCount,
            BigDecimal topTotalScore,
            int finishedBacktests,
            int totalBacktests
    ) {
    }

    public record ContestRankings(
            Page<ContestRankingRow> page,
            long disqualifiedCount
    ) {
    }
}
