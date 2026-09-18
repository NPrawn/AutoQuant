package com.ssafy.a107.trading.simulation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.trading.simulation.service.SimulationReadinessDebugService;
import com.ssafy.a107.trading.simulation.service.SimulationStrategyReadinessEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SimulationReadinessDebugControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SIMULATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ASSET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant EXECUTION_AT = Instant.parse("2026-09-18T02:00:00Z");
    private static final Instant WARMUP_FROM = Instant.parse("2026-09-17T05:00:00Z");

    @Mock
    private SimulationReadinessDebugService simulationReadinessDebugService;

    @BeforeEach
    void setUp() {
        io.restassured.module.mockmvc.RestAssuredMockMvc.mockMvc(MockMvcBuilders
                .standaloneSetup(new SimulationReadinessDebugController(simulationReadinessDebugService))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()
                ))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID), null, List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        io.restassured.module.mockmvc.RestAssuredMockMvc.reset();
        SecurityContextHolder.clearContext();
    }

    @DisplayName("준비 상태 진단은 일반 요청에서 마지막 확정 봉을 포함해 조회한다")
    @Test
    void inspectReadiness() {
        given(simulationReadinessDebugService.inspect(MEMBER_ID, SIMULATION_ID, EXECUTION_AT, false))
                .willReturn(evaluation(SimulationStrategyReadinessEvaluator.State.READY));

        given()
                .queryParam("executionAt", EXECUTION_AT.toString())
                .when()
                .get("/debug/simulations/{simulationId}/readiness", SIMULATION_ID)
                .then()
                .statusCode(200)
                .body("state", equalTo("READY"))
                .body("assets[0].marketCode", equalTo("KRW-BTC"));

        then(simulationReadinessDebugService).should().inspect(MEMBER_ID, SIMULATION_ID, EXECUTION_AT, false);
    }

    @DisplayName("준비 상태 진단은 마지막 확정 봉 누락 재현 옵션을 전달한다")
    @Test
    void inspectReadinessWithHiddenLatestCandle() {
        given(simulationReadinessDebugService.inspect(MEMBER_ID, SIMULATION_ID, EXECUTION_AT, true))
                .willReturn(evaluation(SimulationStrategyReadinessEvaluator.State.WAITING_FOR_CANDLES));

        given()
                .queryParam("executionAt", EXECUTION_AT.toString())
                .queryParam("hideLatestCompletedCandle", true)
                .when()
                .get("/debug/simulations/{simulationId}/readiness", SIMULATION_ID)
                .then()
                .statusCode(200)
                .body("state", equalTo("WAITING_FOR_CANDLES"));

        then(simulationReadinessDebugService).should().inspect(MEMBER_ID, SIMULATION_ID, EXECUTION_AT, true);
    }

    private SimulationStrategyReadinessEvaluator.Evaluation evaluation(
            SimulationStrategyReadinessEvaluator.State state
    ) {
        return new SimulationStrategyReadinessEvaluator.Evaluation(
                state,
                WARMUP_FROM,
                21,
                Map.of(),
                List.of(new SimulationStrategyReadinessEvaluator.AssetReadiness(
                        ASSET_ID,
                        "KRW-BTC",
                        false,
                        state == SimulationStrategyReadinessEvaluator.State.READY
                                ? SimulationStrategyReadinessEvaluator.State.READY
                                : SimulationStrategyReadinessEvaluator.State.WAITING_FOR_LATEST_CANDLE,
                        21,
                        20
                ))
        );
    }
}
