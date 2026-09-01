package com.ssafy.a107.strategy.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class StrategyCommandService {

    private final StrategyRepository strategyRepository;
    private final MemberRepository memberRepository;

    public StrategyCommandService(
            StrategyRepository strategyRepository,
            MemberRepository memberRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.memberRepository = memberRepository;
    }

    public Strategy createStrategy(UUID memberId, String name, String description, StrategyDsl strategyDsl) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.MEMBER_NOT_FOUND));

        return strategyRepository.save(new Strategy(member, name, description, strategyDsl));
    }

    public void deleteStrategy(UUID memberId, Long strategyId) {
        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(strategyId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        strategy.delete(Instant.now());
    }
}
