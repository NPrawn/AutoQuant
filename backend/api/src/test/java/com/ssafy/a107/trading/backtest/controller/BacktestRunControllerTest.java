package com.ssafy.a107.trading.backtest.controller;

import com.epages.restdocs.apispec.EnumFields;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestRunStatus;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestEquityId;
import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.domain.PositionSide;
import com.ssafy.a107.trading.backtest.domain.TradeSide;
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
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.Schema.schema;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class BacktestRunControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID BACKTEST_ID = UUID.fromString("e4b4e9e0-8d17-4a47-98d4-4d9f06d97b2b");

    @Mock
    private BacktestRunCommandService backtestRunCommandService;

    @Mock
    private BacktestRunReadService backtestRunReadService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
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
                .apply(documentationConfiguration(restDocumentation))
                .build();
        io.restassured.module.mockmvc.RestAssuredMockMvc.mockMvc(mockMvc);
        authenticate();
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
                .apply(document(
                        "backtests-create",
                        resource(ResourceSnippetParameters.builder()
                                .tag("백테스트")
                                .summary("백테스트 실행 생성")
                                .description("인증된 사용자의 전략으로 백테스트 실행을 생성하고 QUEUED 상태를 반환합니다.")
                                .requestSchema(schema("CreateBacktestRunRequest"))
                                .responseSchema(schema("BacktestRunCreatedResponse"))
                                .requestFields(createRequestFields())
                                .responseFields(createdResponseFields())
                                .build())
                ))
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
                .apply(document(
                        "backtests-get",
                        resource(ResourceSnippetParameters.builder()
                                .tag("백테스트")
                                .summary("백테스트 실행 단건 조회")
                                .description("인증된 사용자가 생성한 백테스트 실행의 상태와 실행 설정을 조회합니다.")
                                .responseSchema(schema("BacktestRunResponse"))
                                .responseFields(backtestResponseFields())
                                .build())
                ))
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
                .apply(document(
                        "backtests-list",
                        resource(ResourceSnippetParameters.builder()
                                .tag("백테스트")
                                .summary("백테스트 실행 목록 조회")
                                .description("인증된 사용자의 백테스트 실행 목록을 페이지 단위로 조회합니다.")
                                .responseSchema(schema("BacktestRunPageResponse"))
                                .responseFields(pageResponseFields())
                                .build())
                ))
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
                .apply(document(
                        "backtests-create-invalid-request",
                        resource(ResourceSnippetParameters.builder()
                                .tag("백테스트")
                                .summary("백테스트 실행 생성")
                                .description("필수 입력이 누락되면 공통 요청 오류를 반환합니다.")
                                .requestSchema(schema("CreateBacktestRunRequest"))
                                .responseSchema(schema("ApiErrorResponse"))
                                .responseFields(errorResponseFields())
                                .build())
                ))
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
                .apply(document(
                        "backtests-get-not-found",
                        resource(ResourceSnippetParameters.builder()
                                .tag("백테스트")
                                .summary("백테스트 실행 단건 조회")
                                .description("다른 사용자의 백테스트 실행은 조회할 수 없습니다.")
                                .responseSchema(schema("ApiErrorResponse"))
                                .responseFields(errorResponseFields())
                                .build())
                ))
                .statusCode(HttpStatus.NOT_FOUND.value())
                .body("code", equalTo(ApiErrorCode.BACKTEST_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("백테스트 최종 성과를 조회한다")
    void getBacktestResult() {
        BacktestResult result = mock(BacktestResult.class);
        given(result.getBacktestId()).willReturn(BACKTEST_ID);
        given(result.getFinalAssetValue()).willReturn(new BigDecimal("1100000"));
        given(result.getCumulativeReturn()).willReturn(new BigDecimal("0.1"));
        given(result.getCagr()).willReturn(new BigDecimal("0.1"));
        given(result.getMdd()).willReturn(new BigDecimal("0.05"));
        given(result.getSharpeRatio()).willReturn(new BigDecimal("1.2"));
        given(result.getVolatility()).willReturn(new BigDecimal("0.2"));
        given(result.getWinRate()).willReturn(new BigDecimal("0.5"));
        given(result.getTradeCount()).willReturn(3);
        given(result.getTotalFee()).willReturn(new BigDecimal("1000"));
        given(result.getBenchmarkReturn()).willReturn(new BigDecimal("0.08"));
        given(backtestRunReadService.getBacktestResult(MEMBER_ID, BACKTEST_ID))
                .willReturn(result);

        given()
                .when()
                .get("/backtests/{backtestId}/result", BACKTEST_ID)
                .then()
                .apply(document(
                        "backtests-result",
                        resource(ResourceSnippetParameters.builder()
                                .tag("Backtest")
                                .summary("Get backtest result")
                                .description("Returns the completed backtest performance summary.")
                                .responseSchema(schema("BacktestResultResponse"))
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("backtestId", equalTo(BACKTEST_ID.toString()))
                .body("finalAssetValue", equalTo(1100000))
                .body("tradeCount", equalTo(3))
                .body("benchmarkReturn", equalTo(0.08F));

        then(backtestRunReadService).should()
                .getBacktestResult(MEMBER_ID, BACKTEST_ID);
    }

    @Test
    @DisplayName("백테스트 체결과 Equity 이력을 페이지로 조회한다")
    void getBacktestTradesAndEquities() {
        org.springframework.data.domain.Page<BacktestTrade> trades =
                new org.springframework.data.domain.PageImpl<>(List.of(backtestTrade()));
        org.springframework.data.domain.Page<BacktestEquity> equities =
                new org.springframework.data.domain.PageImpl<>(List.of(backtestEquity()));
        given(backtestRunReadService.getBacktestTrades(
                eq(MEMBER_ID),
                eq(BACKTEST_ID),
                any()
        )).willReturn(trades);
        given(backtestRunReadService.getBacktestEquities(
                eq(MEMBER_ID),
                eq(BACKTEST_ID),
                any()
        )).willReturn(equities);

        given()
                .when()
                .get("/backtests/{backtestId}/trades?page=0&size=20", BACKTEST_ID)
                .then()
                .apply(document(
                        "backtests-trades",
                        resource(ResourceSnippetParameters.builder()
                                .tag("Backtest")
                                .summary("Get backtest trades")
                                .description("Returns completed backtest fills in chronological order.")
                                .responseSchema(schema("BacktestTradePageResponse"))
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("content.size()", equalTo(1))
                .body("content[0].assetId", equalTo("11111111-1111-1111-1111-111111111111"))
                .body("content[0].side", equalTo("BUY"));

        authenticate();
        given()
                .when()
                .get("/backtests/{backtestId}/equity?page=0&size=20", BACKTEST_ID)
                .then()
                .apply(document(
                        "backtests-equity",
                        resource(ResourceSnippetParameters.builder()
                                .tag("Backtest")
                                .summary("Get backtest equity")
                                .description("Returns the completed backtest equity curve.")
                                .responseSchema(schema("BacktestEquityPageResponse"))
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("content.size()", equalTo(1))
                .body("content[0].totalAssetValue", equalTo(1100000))
                .body("content[0].drawdownRate", equalTo(0.05F));
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

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(MEMBER_ID),
                        null,
                        List.of()
                )
        );
    }

    private BacktestTrade backtestTrade() {
        UUID assetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Asset asset = mock(Asset.class);
        BacktestTrade trade = mock(BacktestTrade.class);
        given(trade.getTradeId()).willReturn(UUID.fromString(
                "22222222-2222-2222-2222-222222222222"
        ));
        given(trade.getAsset()).willReturn(asset);
        given(asset.getAssetId()).willReturn(assetId);
        given(asset.getExchangeMarketCode()).willReturn("KRW-BTC");
        given(trade.getPositionSide()).willReturn(PositionSide.LONG);
        given(trade.getSide()).willReturn(TradeSide.BUY);
        given(trade.getSignalAt()).willReturn(Instant.parse("2026-01-01T00:00:00Z"));
        given(trade.getFilledAt()).willReturn(Instant.parse("2026-01-01T01:00:00Z"));
        given(trade.getExecutedPrice()).willReturn(new BigDecimal("100000"));
        given(trade.getQuantity()).willReturn(BigDecimal.ONE);
        given(trade.getNotional()).willReturn(new BigDecimal("100000"));
        given(trade.getFeeAmount()).willReturn(new BigDecimal("50"));
        given(trade.getSlippageAmount()).willReturn(new BigDecimal("100"));
        return trade;
    }

    private BacktestEquity backtestEquity() {
        BacktestEquity equity = mock(BacktestEquity.class);
        given(equity.getId()).willReturn(new BacktestEquityId(
                BACKTEST_ID,
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        given(equity.getTotalAssetValue()).willReturn(new BigDecimal("1100000"));
        given(equity.getCashBalance()).willReturn(new BigDecimal("500000"));
        given(equity.getReturnRate()).willReturn(new BigDecimal("0.1"));
        given(equity.getDrawdownRate()).willReturn(new BigDecimal("0.05"));
        return equity;
    }

    private FieldDescriptor[] createRequestFields() {
        EnumFields executionPriceRuleFields = new EnumFields(ExecutionPriceRule.class);
        return new FieldDescriptor[]{
                fieldWithPath("strategyId").type(JsonFieldType.NUMBER).description("실행할 전략 ID"),
                fieldWithPath("periodStart").type(JsonFieldType.STRING).description("백테스트 시작 시각(UTC)"),
                fieldWithPath("periodEnd").type(JsonFieldType.STRING).description("백테스트 종료 시각(UTC)"),
                fieldWithPath("quoteCurrency").type(JsonFieldType.STRING).description("기준 통화(MVP: KRW)"),
                fieldWithPath("initialCapital").type(JsonFieldType.NUMBER).description("초기 자본"),
                fieldWithPath("feeRate").type(JsonFieldType.NUMBER).description("수수료율"),
                fieldWithPath("slippageBps").type(JsonFieldType.NUMBER).description("슬리피지(bps)"),
                executionPriceRuleFields.withPath("executionPriceRule").description("체결 가격 규칙"),
                fieldWithPath("engineVersion").type(JsonFieldType.STRING).description("백테스트 엔진 버전"),
                fieldWithPath("marketDataVersion").type(JsonFieldType.STRING).description("시장 데이터 버전")
        };
    }

    private FieldDescriptor[] createdResponseFields() {
        EnumFields statusFields = new EnumFields(BacktestRunStatus.class);
        return new FieldDescriptor[]{
                fieldWithPath("backtestId").type(JsonFieldType.STRING).description("백테스트 실행 ID"),
                statusFields.withPath("status").description("실행 상태"),
                fieldWithPath("queuedAt").type(JsonFieldType.STRING).description("대기 등록 시각")
        };
    }

    private FieldDescriptor[] backtestResponseFields() {
        EnumFields statusFields = new EnumFields(BacktestRunStatus.class);
        EnumFields timeframeFields = new EnumFields(Timeframe.class);
        EnumFields rebalanceFields = new EnumFields(RebalancePeriod.class);
        EnumFields executionPriceRuleFields = new EnumFields(ExecutionPriceRule.class);
        return new FieldDescriptor[]{
                fieldWithPath("backtestId").type(JsonFieldType.STRING).description("백테스트 실행 ID"),
                fieldWithPath("strategyId").type(JsonFieldType.NUMBER).description("실행한 전략 ID"),
                statusFields.withPath("status").description("실행 상태"),
                fieldWithPath("periodStart").type(JsonFieldType.STRING).description("백테스트 시작 시각"),
                fieldWithPath("periodEnd").type(JsonFieldType.STRING).description("백테스트 종료 시각"),
                timeframeFields.withPath("timeframe").description("전략 봉 주기"),
                fieldWithPath("quoteCurrency").type(JsonFieldType.STRING).description("기준 통화"),
                fieldWithPath("initialCapital").type(JsonFieldType.NUMBER).description("초기 자본"),
                fieldWithPath("feeRate").type(JsonFieldType.NUMBER).description("수수료율"),
                fieldWithPath("slippageBps").type(JsonFieldType.NUMBER).description("슬리피지(bps)"),
                rebalanceFields.withPath("rebalanceInterval").description("리밸런싱 주기"),
                executionPriceRuleFields.withPath("executionPriceRule").description("체결 가격 규칙"),
                fieldWithPath("engineVersion").type(JsonFieldType.STRING).description("백테스트 엔진 버전"),
                fieldWithPath("marketDataVersion").type(JsonFieldType.STRING).description("시장 데이터 버전"),
                fieldWithPath("queuedAt").type(JsonFieldType.STRING).description("대기 등록 시각"),
                fieldWithPath("startedAt").type(JsonFieldType.STRING).description("실행 시작 시각").optional(),
                fieldWithPath("completedAt").type(JsonFieldType.STRING).description("실행 완료 시각").optional()
        };
    }

    private FieldDescriptor[] pageResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("content").type(JsonFieldType.ARRAY).description("백테스트 실행 목록"),
                fieldWithPath("content[].backtestId").type(JsonFieldType.STRING).description("백테스트 실행 ID"),
                fieldWithPath("content[].strategyId").type(JsonFieldType.NUMBER).description("실행한 전략 ID"),
                fieldWithPath("content[].status").type(JsonFieldType.STRING).description("실행 상태"),
                fieldWithPath("content[].periodStart").type(JsonFieldType.STRING).description("백테스트 시작 시각"),
                fieldWithPath("content[].periodEnd").type(JsonFieldType.STRING).description("백테스트 종료 시각"),
                fieldWithPath("content[].timeframe").type(JsonFieldType.STRING).description("전략 봉 주기"),
                fieldWithPath("content[].quoteCurrency").type(JsonFieldType.STRING).description("기준 통화"),
                fieldWithPath("content[].initialCapital").type(JsonFieldType.NUMBER).description("초기 자본"),
                fieldWithPath("content[].feeRate").type(JsonFieldType.NUMBER).description("수수료율"),
                fieldWithPath("content[].slippageBps").type(JsonFieldType.NUMBER).description("슬리피지(bps)"),
                fieldWithPath("content[].rebalanceInterval").type(JsonFieldType.STRING).description("리밸런싱 주기"),
                fieldWithPath("content[].executionPriceRule").type(JsonFieldType.STRING).description("체결 가격 규칙"),
                fieldWithPath("content[].engineVersion").type(JsonFieldType.STRING).description("백테스트 엔진 버전"),
                fieldWithPath("content[].marketDataVersion").type(JsonFieldType.STRING).description("시장 데이터 버전"),
                fieldWithPath("content[].queuedAt").type(JsonFieldType.STRING).description("대기 등록 시각"),
                fieldWithPath("content[].startedAt").type(JsonFieldType.STRING).description("실행 시작 시각").optional(),
                fieldWithPath("content[].completedAt").type(JsonFieldType.STRING).description("실행 완료 시각").optional(),
                fieldWithPath("page").type(JsonFieldType.NUMBER).description("현재 페이지 번호"),
                fieldWithPath("size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("전체 실행 수"),
                fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수")
        };
    }

    private FieldDescriptor[] errorResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("timestamp").type(JsonFieldType.STRING).description("오류 발생 시각"),
                fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
                fieldWithPath("code").type(JsonFieldType.STRING).description("API 오류 코드"),
                fieldWithPath("message").type(JsonFieldType.STRING).description("오류 메시지"),
                fieldWithPath("path").type(JsonFieldType.STRING).description("오류 요청 경로"),
                fieldWithPath("validationErrors").type(JsonFieldType.ARRAY).description("요청 검증 오류 목록"),
                fieldWithPath("validationErrors[].field").type(JsonFieldType.STRING).description("검증에 실패한 필드").optional(),
                fieldWithPath("validationErrors[].reason").type(JsonFieldType.STRING).description("필드 검증 실패 사유").optional()
        };
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
