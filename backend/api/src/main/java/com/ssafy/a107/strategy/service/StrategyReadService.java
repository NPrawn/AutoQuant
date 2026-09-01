package com.ssafy.a107.strategy.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StrategyReadService {

    private final StrategyRepository strategyRepository;

    public StrategyReadService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    public List<Strategy> getStrategies(UUID memberId) {
        return strategyRepository.findAllByMemberMemberIdAndDeletedAtIsNull(memberId);
    }

    public Strategy getStrategy(UUID memberId, Long strategyId) {
        return strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(strategyId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));
    }
}
