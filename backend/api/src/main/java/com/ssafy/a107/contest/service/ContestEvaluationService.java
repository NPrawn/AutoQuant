package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestEvaluation;
import com.ssafy.a107.contest.domain.ContestScoringPolicy;
import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.repository.ContestEvaluationRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.service.BacktestRunCommandService;
import com.ssafy.a107.trading.backtest.service.CreateContestBacktestRunCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/*
 * 제출 하나를 데이터셋 4벌에 돌린다. 제출하는 즉시 부르고, 마감 뒤 채점도 빠진 제출에만 같은 것을 부른다.
 * 교체 제출이면 이전 평가와 점수를 버린다. 마지막 제출만 채점 대상이다.
 */
@Service
public class ContestEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ContestEvaluationService.class);
    static final String ENGINE_VERSION = "contest-v1";

    private final MarketDatasetRepository marketDatasetRepository;
    private final ContestEvaluationRepository contestEvaluationRepository;
    private final ContestResultRepository contestResultRepository;
    private final BacktestRunCommandService backtestRunCommandService;

    public ContestEvaluationService(
            MarketDatasetRepository marketDatasetRepository,
            ContestEvaluationRepository contestEvaluationRepository,
            ContestResultRepository contestResultRepository,
            BacktestRunCommandService backtestRunCommandService
    ) {
        this.marketDatasetRepository = marketDatasetRepository;
        this.contestEvaluationRepository = contestEvaluationRepository;
        this.contestResultRepository = contestResultRepository;
        this.backtestRunCommandService = backtestRunCommandService;
    }

    /** 데이터셋이 4벌 다 있어야 돌린다. 만든 백테스트 수를 돌려준다. */
    @Transactional
    public int evaluate(ContestEntry entry) {
        UUID roundId = entry.getRound().getRoundId();
        List<MarketDataset> datasets = marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(roundId);
        if (datasets.size() != MarketDatasetKind.values().length) {
            log.warn("대회 회차 {} 는 데이터셋이 {}벌이라 제출을 돌리지 않는다", roundId, datasets.size());
            return 0;
        }

        contestEvaluationRepository.deleteAllByIdEntryId(entry.getEntryId());
        contestResultRepository.deleteByEntryId(entry.getEntryId());

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
        return datasets.size();
    }
}
