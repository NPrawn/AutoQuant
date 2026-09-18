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

    /* 공개 여부는 주인만 바꾼다. 남의 전략은 있는지조차 알리지 않는다. */
    public Strategy changeVisibility(UUID memberId, Long strategyId, boolean published) {
        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(strategyId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        strategy.changeVisibility(published);
        return strategy;
    }

    /*
     * 복제 대상은 내 전략이거나 공개된 전략이다. 비공개인 남의 전략은 찾을 수 없음으로 답한다.
     * 권한 없음으로 답하면 그 번호에 전략이 있다는 사실이 새어 나간다.
     */
    public Strategy copyStrategy(UUID memberId, Long strategyId) {
        Strategy source = strategyRepository.findByStrategyIdAndDeletedAtIsNull(strategyId)
                .filter(strategy -> strategy.isOwnedBy(memberId) || strategy.isPublished())
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        Member owner = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.MEMBER_NOT_FOUND));

        return strategyRepository.save(source.copyFor(owner));
    }

    public void deleteStrategy(UUID memberId, Long strategyId) {
        Strategy strategy = strategyRepository
                .findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(strategyId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.STRATEGY_NOT_FOUND));

        strategy.delete(Instant.now());
    }
}
