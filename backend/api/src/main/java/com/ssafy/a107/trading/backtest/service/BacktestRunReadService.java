package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BacktestRunReadService {

    private final BacktestRunRepository backtestRunRepository;

    public BacktestRunReadService(BacktestRunRepository backtestRunRepository) {
        this.backtestRunRepository = backtestRunRepository;
    }

    public Page<BacktestRun> getBacktestRuns(UUID memberId, Pageable pageable) {
        return backtestRunRepository.findAllByMemberMemberIdOrderByQueuedAtDesc(memberId, pageable);
    }

    public BacktestRun getBacktestRun(UUID memberId, UUID backtestId) {
        return backtestRunRepository.findByBacktestIdAndMemberMemberId(backtestId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.BACKTEST_NOT_FOUND));
    }
}
