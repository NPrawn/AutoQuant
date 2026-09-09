package com.ssafy.a107.trading.simulation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import com.ssafy.a107.trading.simulation.service.CreateSimulationRunCommand;
import com.ssafy.a107.trading.simulation.service.SimulationRunCommandService;
import com.ssafy.a107.trading.simulation.service.SimulationRunReadService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class SimulationRunControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SIMULATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private SimulationRunCommandService simulationRunCommandService;

    @Mock
    private SimulationRunReadService simulationRunReadService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        SimulationRunController controller = new SimulationRunController(
                simulationRunCommandService,
                simulationRunReadService
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation))
                .build();
        io.restassured.module.mockmvc.RestAssuredMockMvc.mockMvc(mockMvc);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID), null, List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        io.restassured.module.mockmvc.RestAssuredMockMvc.reset();
        SecurityContextHolder.clearContext();
    }

    @DisplayName("유효한 모의투자 생성 요청은 생성 상태와 식별자를 반환한다")
    @Test
    void createSimulationRun() {
        SimulationRun simulationRun = mock(SimulationRun.class);
        given(simulationRun.getSimulationId()).willReturn(SIMULATION_ID);
        given(simulationRun.getStatus()).willReturn(SimulationRunStatus.CREATED);
        given(simulationRunCommandService.createSimulationRun(eq(MEMBER_ID), any(CreateSimulationRunCommand.class)))
                .willReturn(simulationRun);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "strategyId": 1,
                          "initialCapital": 1000000,
                          "feeRate": 0.0005,
                          "slippageBps": 5,
                          "executionPriceRule": "NEXT_CANDLE_OPEN",
                          "engineVersion": "engine-v1"
                        }
                        """)
                .when()
                .post("/simulations")
                .then()
                .apply(document("simulations-create"))
                .statusCode(201)
                .body("simulationId", equalTo(SIMULATION_ID.toString()))
                .body("status", equalTo("CREATED"));

        then(simulationRunCommandService).should()
                .createSimulationRun(eq(MEMBER_ID), any(CreateSimulationRunCommand.class));
    }
}
