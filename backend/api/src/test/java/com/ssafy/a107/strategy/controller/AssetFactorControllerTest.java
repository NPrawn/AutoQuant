package com.ssafy.a107.strategy.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.service.AssetFactorReadService;
import com.ssafy.a107.strategy.service.AssetFactorSnapshot;
import com.ssafy.a107.strategy.service.AssetFactorValue;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class AssetFactorControllerTest {

    private static final String PATH = "/assets/{assetId}/factors";
    private static final UUID ASSET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant AS_OF = Instant.parse("2026-09-13T00:00:00Z");

    @Mock
    private AssetFactorReadService assetFactorReadService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider documentation) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new AssetFactorController(assetFactorReadService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .apply(documentationConfiguration(documentation))
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @Test
    @DisplayName("전략 엔진이 쓰는 여섯 팩터의 현재 값을 반환하고 문서화한다")
    void getCurrentFactors() {
        given(assetFactorReadService.findCurrent(ASSET_ID, Timeframe.ONE_DAY)).willReturn(snapshot());

        given().queryParam("timeframe", "1D")
                .when().get(PATH, ASSET_ID)
                .then()
                .apply(document("asset-factors",
                        resource(baseResource()
                                .responseSchema(schema("AssetFactorsResponse"))
                                .responseFields(
                                        fieldWithPath("assetId").type(JsonFieldType.STRING).description("자산 UUID"),
                                        fieldWithPath("timeframe").type(JsonFieldType.STRING).description("계산에 쓴 봉 주기. 1H, 4H, 1D"),
                                        fieldWithPath("asOf").type(JsonFieldType.STRING).optional().description("값을 만든 마지막 확정 봉의 시작 시각. 봉이 없으면 null"),
                                        fieldWithPath("factors").type(JsonFieldType.ARRAY).description("팩터 목록. 순서는 고정이다"),
                                        fieldWithPath("factors[].factor").type(JsonFieldType.STRING).description("팩터. RETURN, RSI, SMA_DISPARITY, VOLATILITY, VOLUME_RATIO, DRAWDOWN"),
                                        fieldWithPath("factors[].period").type(JsonFieldType.NUMBER).description("서버가 정한 기간. 봉 개수다"),
                                        fieldWithPath("factors[].value").type(JsonFieldType.NUMBER).optional().description("현재 값. 봉이 기간을 채우지 못하면 null")
                                ).build())))
                .statusCode(200)
                .body("assetId", equalTo(ASSET_ID.toString()))
                .body("timeframe", equalTo("1D"))
                .body("asOf", equalTo("2026-09-13T00:00:00Z"))
                .body("factors", hasSize(2))
                .body("factors[0].factor", equalTo("RETURN"))
                .body("factors[0].period", equalTo(20))
                .body("factors[0].value", equalTo(4.1f))
                .body("factors[1].factor", equalTo("DRAWDOWN"))
                .body("factors[1].value", nullValue());
    }

    @Test
    @DisplayName("봉 주기를 생략하면 일봉으로 조회한다")
    void defaultTimeframe() {
        given(assetFactorReadService.findCurrent(ASSET_ID, Timeframe.ONE_DAY)).willReturn(snapshot());

        given().when().get(PATH, ASSET_ID).then().statusCode(200).body("timeframe", equalTo("1D"));
        then(assetFactorReadService).should().findCurrent(ASSET_ID, Timeframe.ONE_DAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1H", "4H", "1D"})
    @DisplayName("지원하는 봉 주기를 그대로 계산에 넘긴다")
    void supportedTimeframes(String timeframe) {
        Timeframe expected = Timeframe.fromValue(timeframe);
        given(assetFactorReadService.findCurrent(ASSET_ID, expected)).willReturn(snapshot());

        given().queryParam("timeframe", timeframe).when().get(PATH, ASSET_ID).then()
                .statusCode(200).body("timeframe", equalTo(timeframe));
        then(assetFactorReadService).should().findCurrent(ASSET_ID, expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1M", "5m", "1d", "text"})
    @DisplayName("지원하지 않는 봉 주기는 조회하지 않고 400을 반환한다")
    void rejectUnsupportedTimeframe(String timeframe) {
        given().queryParam("timeframe", timeframe).when().get(PATH, ASSET_ID).then()
                .statusCode(400).body("code", equalTo("COMMON-400-001"));
        then(assetFactorReadService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 자산 UUID는 400을 반환한다")
    void rejectInvalidAssetId() {
        given().when().get(PATH, "not-a-uuid").then()
                .statusCode(400).body("code", equalTo("COMMON-400-001"));
        then(assetFactorReadService).shouldHaveNoInteractions();
    }

    private AssetFactorSnapshot snapshot() {
        return new AssetFactorSnapshot(AS_OF, List.of(
                new AssetFactorValue(FactorType.RETURN, 20, new BigDecimal("4.100000")),
                new AssetFactorValue(FactorType.DRAWDOWN, 60, null)));
    }

    private ResourceSnippetParametersBuilder baseResource() {
        return ResourceSnippetParameters.builder()
                .tag("전략")
                .summary("종목별 현재 팩터 값 조회")
                .description("""
                        JWT 쿠키 또는 HTTP Bearer 인증이 필요합니다.
                        전략 엔진이 선별과 순위에 쓰는 계산기로 같은 값을 계산해 돌려줍니다. 화면이 따로 계산하지 않습니다.
                        기간은 서버가 정합니다. RSI는 14, 낙폭은 60, 나머지는 20이며 요청으로 바꿀 수 없습니다.
                        값의 단위는 전략 DSL과 같습니다. 수익률, SMA 이격률, 변동성, 낙폭은 백분율(4.1 = 4.1%)이고 거래량 비율은 배수입니다.
                        봉이 기간을 채우지 못한 팩터는 value가 null입니다. 오류가 아닙니다.
                        봉이 없으면(미등록 UUID 포함) 200과 값이 모두 null인 목록을 반환합니다.
                        timeframe은 1H, 4H, 1D만 지원하며 생략하면 1D입니다. 그 밖의 값과 잘못된 UUID는 COMMON-400-001(400)입니다.
                        """)
                .pathParameters(parameterWithName("assetId").description("자산 UUID"))
                .queryParameters(parameterWithName("timeframe").optional().description("봉 주기. 1H, 4H, 1D, 기본 1D"));
    }
}
