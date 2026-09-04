package com.ssafy.a107.trading.backtest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.service.BacktestRunCommandService;
import com.ssafy.a107.trading.backtest.service.BacktestRunReadService;
import com.ssafy.a107.trading.backtest.service.CreateBacktestRunCommand;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class BacktestRunControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID BACKTEST_ID = UUID.fromString("e4b4e9e0-8d17-4a47-98d4-4d9f06d97b2b");

    @Mock
    private BacktestRunCommandService backtestRunCommandService;

    @Mock
    private BacktestRunReadService backtestRunReadService;

    @BeforeEach
    void setUp() {
        BacktestRunController controller = new BacktestRunController(
                backtestRunCommandService,
                backtestRunReadService
        );
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        io.restassured.module.mockmvc.RestAssuredMockMvc.mockMvc(mockMvc);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID),
                null,
                List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        io.restassured.module.mockmvc.RestAssuredMockMvc.reset();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 백테스트 요청은 ID와 QUEUED 상태를 즉시 반환한다")
    void createBacktestRun() {
        BacktestRun expected = mock(BacktestRun.class);
        given(expected.getBacktestId()).willReturn(BACKTEST_ID);
        given(expected.getStatus()).willReturn(BacktestRunStatus.QUEUED);
        given(expected.getQueuedAt()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));
        given(backtestRunCommandService.createBacktestRun(eq(MEMBER_ID), any(CreateBacktestRunCommand.class)))
                .willReturn(expected);

        given()
                .contentType(ContentType.JSON)
                .body(validRequest())
                .when()
                .post("/backtests")
                .then()
                .statusCode(HttpStatus.ACCEPTED.value())
                .body("backtestId", equalTo(BACKTEST_ID.toString()))
                .body("status", equalTo("QUEUED"));
    }

    @Test
    @DisplayName("백테스트 실행 단건 조회는 인증 회원의 실행만 조회한다")
    void getBacktestRun() {
        BacktestRun expected = backtestRun();
        given(backtestRunReadService.getBacktestRun(MEMBER_ID, BACKTEST_ID)).willReturn(expected);

        given()
                .when()
                .get("/backtests/{backtestId}", BACKTEST_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("backtestId", equalTo(BACKTEST_ID.toString()))
                .body("strategyId", equalTo(1))
                .body("status", equalTo("QUEUED"));
    }

    @Test
    @DisplayName("백테스트 실행 목록은 페이지 메타데이터와 함께 반환한다")
    void getBacktestRuns() {
        org.springframework.data.domain.Page<BacktestRun> page = new org.springframework.data.domain.PageImpl<>(
                List.of(backtestRun()),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1
        );
        given(backtestRunReadService.getBacktestRuns(eq(MEMBER_ID), any())).willReturn(page);

        given()
                .when()
                .get("/backtests?page=0&size=20")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("content.size()", equalTo(1))
                .body("page", equalTo(0))
                .body("totalElements", equalTo(1));
    }

    @Test
    @DisplayName("필수 입력이 없는 백테스트 요청은 생성 서비스에 전달하지 않는다")
    void rejectInvalidRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"strategyId\":1}")
                .when()
                .post("/backtests")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo(ApiErrorCode.INVALID_REQUEST.getCode()));

        then(backtestRunCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("백테스트 목록 페이지 크기 제한을 넘으면 400으로 응답한다")
    void rejectInvalidPageSize() {
        given()
                .when()
                .get("/backtests?page=0&size=101")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo(ApiErrorCode.INVALID_REQUEST.getCode()));

        then(backtestRunReadService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 회원의 백테스트 실행은 찾을 수 없는 것으로 응답한다")
    void rejectBacktestRunOutsideMemberOwnership() {
        given(backtestRunReadService.getBacktestRun(MEMBER_ID, BACKTEST_ID))
                .willThrow(new ApiException(ApiErrorCode.BACKTEST_NOT_FOUND));

        given()
                .when()
                .get("/backtests/{backtestId}", BACKTEST_ID)
                .then()
                .statusCode(HttpStatus.NOT_FOUND.value())
                .body("code", equalTo(ApiErrorCode.BACKTEST_NOT_FOUND.getCode()));
    }

    private BacktestRun backtestRun() {
        BacktestRun backtestRun = mock(BacktestRun.class);
        Strategy strategy = mock(Strategy.class);
        given(backtestRun.getBacktestId()).willReturn(BACKTEST_ID);
        given(backtestRun.getStrategy()).willReturn(strategy);
        given(strategy.getStrategyId()).willReturn(1L);
        given(backtestRun.getStatus()).willReturn(BacktestRunStatus.QUEUED);
        given(backtestRun.getPeriodStart()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));
        given(backtestRun.getPeriodEnd()).willReturn(Instant.parse("2026-02-01T00:00:00Z"));
        given(backtestRun.getTimeframe()).willReturn(Timeframe.ONE_HOUR);
        given(backtestRun.getQuoteCurrency()).willReturn("KRW");
        given(backtestRun.getInitialCapital()).willReturn(BigDecimal.valueOf(1_000_000));
        given(backtestRun.getFeeRate()).willReturn(BigDecimal.valueOf(0.0005));
        given(backtestRun.getSlippageBps()).willReturn(BigDecimal.valueOf(5));
        given(backtestRun.getRebalanceInterval()).willReturn(RebalancePeriod.WEEKLY);
        given(backtestRun.getExecutionPriceRule()).willReturn(ExecutionPriceRule.NEXT_CANDLE_OPEN);
        given(backtestRun.getEngineVersion()).willReturn("engine-v1");
        given(backtestRun.getMarketDataVersion()).willReturn("market-v1");
        given(backtestRun.getQueuedAt()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));
        return backtestRun;
    }

    private String validRequest() {
        return """
                {
                  "strategyId": 1,
                  "periodStart": "2026-01-01T00:00:00Z",
                  "periodEnd": "2026-02-01T00:00:00Z",
                  "quoteCurrency": "KRW",
                  "initialCapital": 1000000,
                  "feeRate": 0.0005,
                  "slippageBps": 5,
                  "executionPriceRule": "NEXT_CANDLE_OPEN",
                  "engineVersion": "engine-v1",
                  "marketDataVersion": "market-v1"
                }
                """;
    }
}
