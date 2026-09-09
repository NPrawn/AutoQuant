package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SimulationRunCommandServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SIMULATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private SimulationRunRepository simulationRunRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @InjectMocks
    private SimulationRunCommandService simulationRunCommandService;

    @DisplayName("모의투자 생성은 사용자 소유 전략으로만 저장한다")
    @Test
    void createSimulationRun() {
        Strategy strategy = strategy();
        given(strategyRepository.findByStrategyIdAndMemberMemberIdAndDeletedAtIsNull(1L, MEMBER_ID))
                .willReturn(Optional.of(strategy));
        given(simulationRunRepository.save(any(SimulationRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        simulationRunCommandService.createSimulationRun(MEMBER_ID, command());

        then(simulationRunRepository).should().save(any(SimulationRun.class));
    }

    @DisplayName("유효하지 않은 초기 자본은 전략을 조회하지 않고 거절한다")
    @Test
    void rejectInvalidInitialCapital() {
        CreateSimulationRunCommand invalidCommand = new CreateSimulationRunCommand(
                1L, BigDecimal.ZERO, BigDecimal.valueOf(0.0005), BigDecimal.valueOf(5),
                ExecutionPriceRule.NEXT_CANDLE_OPEN, "engine-v1"
        );

        assertThatThrownBy(() -> simulationRunCommandService.createSimulationRun(MEMBER_ID, invalidCommand))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.SIMULATION_INVALID_CONFIGURATION);

        then(strategyRepository).shouldHaveNoInteractions();
    }

    @DisplayName("다른 사용자는 모의투자 실행을 시작할 수 없다")
    @Test
    void cannotStartOtherMembersSimulation() {
        SimulationRun simulationRun = org.mockito.Mockito.mock(SimulationRun.class);
        Member member = org.mockito.Mockito.mock(Member.class);
        given(member.getMemberId()).willReturn(UUID.randomUUID());
        given(simulationRun.getMember()).willReturn(member);
        given(simulationRunRepository.findBySimulationIdForUpdate(SIMULATION_ID)).willReturn(Optional.of(simulationRun));

        assertThatThrownBy(() -> simulationRunCommandService.startSimulationRun(MEMBER_ID, SIMULATION_ID))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.SIMULATION_NOT_FOUND);

        then(simulationRun).should(org.mockito.Mockito.never()).start(any());
    }

    @DisplayName("시작할 수 없는 상태의 모의투자는 상태 전이 오류를 반환한다")
    @Test
    void rejectInvalidStatusTransitionWhenStartingSimulation() {
        SimulationRun simulationRun = org.mockito.Mockito.mock(SimulationRun.class);
        Member member = org.mockito.Mockito.mock(Member.class);
        given(member.getMemberId()).willReturn(MEMBER_ID);
        given(simulationRun.getMember()).willReturn(member);
        given(simulationRunRepository.findBySimulationIdForUpdate(SIMULATION_ID)).willReturn(Optional.of(simulationRun));
        org.mockito.Mockito.doThrow(new IllegalStateException()).when(simulationRun).start(any());

        assertThatThrownBy(() -> simulationRunCommandService.startSimulationRun(MEMBER_ID, SIMULATION_ID))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.SIMULATION_INVALID_STATUS_TRANSITION);
    }

    private CreateSimulationRunCommand command() {
        return new CreateSimulationRunCommand(
                1L, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(0.0005), BigDecimal.valueOf(5),
                ExecutionPriceRule.NEXT_CANDLE_OPEN, "engine-v1"
        );
    }

    private Strategy strategy() {
        Member member = new Member(
                LoginProvider.SSAFY,
                "simulation-service-member",
                "simulation-service@example.com",
                "모의투자 사용자"
        );
        StrategyDsl strategyDsl = new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.DAILY)
        );
        return new Strategy(member, "모의투자 전략", null, strategyDsl);
    }
}
