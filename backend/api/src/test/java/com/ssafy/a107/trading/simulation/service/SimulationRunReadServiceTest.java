package com.ssafy.a107.trading.simulation.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.simulation.repository.SimulationEquityRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationPositionRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationResultRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationRunRepository;
import com.ssafy.a107.trading.simulation.repository.SimulationTradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SimulationRunReadServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SIMULATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private SimulationRunRepository simulationRunRepository;

    @Mock
    private SimulationResultRepository simulationResultRepository;

    @Mock
    private SimulationPositionRepository simulationPositionRepository;

    @Mock
    private SimulationTradeRepository simulationTradeRepository;

    @Mock
    private SimulationEquityRepository simulationEquityRepository;

    @InjectMocks
    private SimulationRunReadService simulationRunReadService;

    @Test
    void returnSimulationNotFoundWhenSimulationDoesNotBelongToMember() {
        given(simulationRunRepository.findBySimulationIdAndMemberMemberId(SIMULATION_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> simulationRunReadService.getSimulationRun(MEMBER_ID, SIMULATION_ID))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ApiErrorCode.SIMULATION_NOT_FOUND);
    }
}
