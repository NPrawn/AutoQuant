package com.ssafy.a107.trading.simulation.controller;

import com.ssafy.a107.trading.simulation.service.SimulationReadinessDebugService;
import com.ssafy.a107.trading.simulation.service.SimulationDebugCommandService;
import com.ssafy.a107.auth.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(SimulationReadinessDebugController.class)
class SimulationReadinessDebugControllerContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private SimulationReadinessDebugService simulationReadinessDebugService;

    @MockBean
    private SimulationDebugCommandService simulationDebugCommandService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @DisplayName("기본 프로필에서도 모의투자 준비 상태 진단 컨트롤러가 등록된다")
    @Test
    void registersWithoutDebugProfile() {
        assertThat(applicationContext.getBean(SimulationReadinessDebugController.class)).isNotNull();
    }
}
