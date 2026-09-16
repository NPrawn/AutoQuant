package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationRequestedEvent;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SimulationExecutionFailureService {

    private final SimulationRunRepository simulationRunRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SimulationExecutionFailureService(
            SimulationRunRepository simulationRunRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.simulationRunRepository = simulationRunRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID simulationId, RuntimeException exception) {
        simulationRunRepository.findBySimulationIdForUpdate(simulationId)
                .filter(simulationRun -> simulationRun.getStatus() == SimulationRunStatus.CREATED
                        || simulationRun.getStatus() == SimulationRunStatus.RUNNING)
                .ifPresent(simulationRun -> {
                    simulationRun.fail(exception.getClass().getSimpleName());
                    notifyStopped(simulationRun);
                });
    }

    /*
     * 사용자가 멈춘 것이 아니라 엔진이 멈춘 경우다. 화면을 보고 있지 않으면 알 길이 없어 알린다.
     * 내부 예외 이름은 넣지 않는다. 사용자가 할 수 있는 일만 적는다.
     */
    private void notifyStopped(SimulationRun simulationRun) {
        applicationEventPublisher.publishEvent(new NotificationRequestedEvent(
                simulationRun.getMember().getMemberId(),
                NotificationType.SIMULATION_FAILED,
                "%s 모의투자가 멈췄습니다".formatted(simulationRun.getStrategy().getName()),
                "실행 중 문제가 생겨 신호 수신을 중단했습니다. 보유 종목은 그대로 남아 있습니다. 다시 시작할 수 있습니다.",
                simulationRun.getSimulationId().toString()
        ));
    }
}
