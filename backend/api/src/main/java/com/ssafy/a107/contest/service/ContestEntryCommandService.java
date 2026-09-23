package com.ssafy.a107.contest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestFactorPeriodPolicy;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.engine.marketdata.BacktestWarmupCandleCountResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ContestEntryCommandService {

    private final ContestRoundRepository contestRoundRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final StrategyRepository strategyRepository;
    private final BacktestWarmupCandleCountResolver warmupCandleCountResolver;
    private final ContestEvaluationService contestEvaluationService;

    public ContestEntryCommandService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository,
            StrategyRepository strategyRepository,
            BacktestWarmupCandleCountResolver warmupCandleCountResolver,
            ContestEvaluationService contestEvaluationService
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.strategyRepository = strategyRepository;
        this.warmupCandleCountResolver = warmupCandleCountResolver;
        this.contestEvaluationService = contestEvaluationService;
    }

    /*
     * 검증 순서는 회차 → 제출 기간 → 본인 전략 → 팩터 기간 상한이다 (대회 기능 설계 5절).
     * 제출마다 행을 새로 만든다. 같은 전략을 다시 내도 별개의 제출이고 순위에 따로 오른다.
     * 통과하면 그 자리에서 데이터셋 4벌을 돌린다.
     */
    public ContestEntry submit(UUID memberId, UUID roundId, Long strategyId) {
        Instant now = Instant.now();

        ContestRound round = contestRoundRepository.findById(roundId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ROUND_NOT_FOUND));
        if (!round.isSubmittable(now)) {
            throw new ApiException(ApiErrorCode.CONTEST_SUBMISSION_CLOSED);
        }

        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(strategyId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));
        requireFactorPeriodWithinCap(strategy.getStrategyDsl(), round);

        ContestEntry entry = contestEntryRepository.save(
                new ContestEntry(round, strategy.getMember(), strategy, now));
        contestEvaluationService.evaluate(entry);
        return entry;
    }

    private void requireFactorPeriodWithinCap(StrategyDsl strategyDsl, ContestRound round) {
        int maximumFactorPeriod = warmupCandleCountResolver.resolve(strategyDsl) - 1;

        if (maximumFactorPeriod > ContestFactorPeriodPolicy.cap(round.getWarmupWeeks(), strategyDsl.timeframe())) {
            throw new ApiException(ApiErrorCode.CONTEST_FACTOR_PERIOD_TOO_LONG);
        }
    }
}
