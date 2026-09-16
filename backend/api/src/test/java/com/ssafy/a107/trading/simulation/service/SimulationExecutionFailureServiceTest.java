package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SimulationExecutionFailureServiceTest {

    private static final UUID SIMULATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String STRATEGY_NAME = "RSI 눌림목";

    @Mock
    private SimulationRunRepository simulationRunRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SimulationExecutionFailureService simulationExecutionFailureService;

    @DisplayName("실행 중 오류는 별도 실패 전이 서비스에서 FAILED 상태로 기록한다")
    @Test
    void failRunningSimulation() {
        SimulationRun simulationRun = notifiableRun();
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRunRepository.findBySimulationIdForUpdate(SIMULATION_ID))
                .willReturn(Optional.of(simulationRun));

        simulationExecutionFailureService.fail(SIMULATION_ID, new IllegalStateException());

        then(simulationRun).should().fail("IllegalStateException");
    }

    @DisplayName("엔진이 멈춘 모의투자는 사용자에게 알리고 내부 예외 이름은 담지 않는다")
    @Test
    void publishStoppedNotification() {
        SimulationRun simulationRun = notifiableRun();
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRunRepository.findBySimulationIdForUpdate(SIMULATION_ID))
                .willReturn(Optional.of(simulationRun));

        simulationExecutionFailureService.fail(SIMULATION_ID, new IllegalStateException("broker timeout"));

        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        then(applicationEventPublisher).should().publishEvent(event.capture());
        assertThat(event.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().notificationType()).isEqualTo(NotificationType.SIMULATION_FAILED);
        assertThat(event.getValue().title()).contains(STRATEGY_NAME);
        assertThat(event.getValue().body()).doesNotContain("IllegalStateException");
        assertThat(event.getValue().targetId()).isEqualTo(SIMULATION_ID.toString());
    }

    @DisplayName("이미 끝난 모의투자는 상태를 바꾸지도 알리지도 않는다")
    @Test
    void ignoreAlreadyStoppedSimulation() {
        SimulationRun simulationRun = mock(SimulationRun.class);
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.STOPPED);
        given(simulationRunRepository.findBySimulationIdForUpdate(SIMULATION_ID))
                .willReturn(Optional.of(simulationRun));

        simulationExecutionFailureService.fail(SIMULATION_ID, new IllegalStateException());

        then(applicationEventPublisher).shouldHaveNoInteractions();
    }

    private SimulationRun notifiableRun() {
        SimulationRun simulationRun = mock(SimulationRun.class);
        Member member = mock(Member.class);
        Strategy strategy = mock(Strategy.class);
        given(member.getMemberId()).willReturn(MEMBER_ID);
        given(strategy.getName()).willReturn(STRATEGY_NAME);
        given(simulationRun.getMember()).willReturn(member);
        given(simulationRun.getStrategy()).willReturn(strategy);
        given(simulationRun.getSimulationId()).willReturn(SIMULATION_ID);
        return simulationRun;
    }
}
