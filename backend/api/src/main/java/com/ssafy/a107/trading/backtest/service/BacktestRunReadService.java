package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestTradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BacktestRunReadService {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestTradeRepository backtestTradeRepository;
    private final BacktestEquityRepository backtestEquityRepository;

    public BacktestRunReadService(
            BacktestRunRepository backtestRunRepository,
            BacktestResultRepository backtestResultRepository,
            BacktestTradeRepository backtestTradeRepository,
            BacktestEquityRepository backtestEquityRepository
    ) {
        this.backtestRunRepository = backtestRunRepository;
        this.backtestResultRepository = backtestResultRepository;
        this.backtestTradeRepository = backtestTradeRepository;
        this.backtestEquityRepository = backtestEquityRepository;
    }

    public Page<BacktestRun> getBacktestRuns(UUID memberId, Pageable pageable) {
        return backtestRunRepository.findAllByMemberMemberIdAndContestDatasetIdIsNullOrderByQueuedAtDesc(
                memberId,
                pageable
        );
    }

    public BacktestRun getBacktestRun(UUID memberId, UUID backtestId) {
        return backtestRunRepository
                .findByBacktestIdAndMemberMemberIdAndContestDatasetIdIsNull(backtestId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.BACKTEST_NOT_FOUND));
    }

    public BacktestResult getBacktestResult(UUID memberId, UUID backtestId) {
        getBacktestRun(memberId, backtestId);

        return backtestResultRepository.findById(backtestId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.BACKTEST_NOT_FOUND));
    }

    public Page<BacktestTrade> getBacktestTrades(
            UUID memberId,
            UUID backtestId,
            Pageable pageable
    ) {
        getBacktestRun(memberId, backtestId);

        return backtestTradeRepository
                .findAllByBacktestRunBacktestIdOrderByFilledAtAscTradeIdAsc(
                        backtestId,
                        pageable
                );
    }

    public Page<BacktestEquity> getBacktestEquities(
            UUID memberId,
            UUID backtestId,
            Pageable pageable
    ) {
        getBacktestRun(memberId, backtestId);

        return backtestEquityRepository
                .findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(
                        backtestId,
                        pageable
                );
    }
}
