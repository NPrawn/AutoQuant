package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SimulationExecutionFailureServiceTest {

    @Mock
    private SimulationRunRepository simulationRunRepository;

    @InjectMocks
    private SimulationExecutionFailureService simulationExecutionFailureService;

    @DisplayName("실행 중 오류는 별도 실패 전이 서비스에서 FAILED 상태로 기록한다")
    @Test
    void failRunningSimulation() {
        UUID simulationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SimulationRun simulationRun = mock(SimulationRun.class);
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.RUNNING);
        given(simulationRunRepository.findBySimulationIdForUpdate(simulationId)).willReturn(Optional.of(simulationRun));

        simulationExecutionFailureService.fail(simulationId, new IllegalStateException());

        then(simulationRun).should().fail("IllegalStateException");
    }
}
