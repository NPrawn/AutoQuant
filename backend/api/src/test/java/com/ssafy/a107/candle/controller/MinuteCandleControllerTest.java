package com.ssafy.a107.candle.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.candle.service.RecentMinuteCandleFacade;
import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
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

import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.Schema.schema;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.ASSET_ID;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.OPEN_TIME;
import static com.ssafy.a107.candle.RecentMinuteCandleFixtures.candle;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class MinuteCandleControllerTest {

    private static final String PATH = "/api/assets/{assetId}/candles/{intervalMinutes}m";
    @Mock
    private RecentMinuteCandleFacade facade;

    @BeforeEach
    void setUp(RestDocumentationContextProvider documentation) {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new MinuteCandleController(facade))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().registerModule(new JavaTimeModule())))
                .apply(documentationConfiguration(documentation))
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 15, 60, 240, 1440})
    @DisplayName("지원 간격의 최근 확정 봉을 시간 오름차순과 문자열 가격으로 반환하고 문서화한다")
    void getRecentCandles(int interval) {
        long width = interval * 60_000L;
        long start = OPEN_TIME / width * width;
        given(facade.findRecent(ASSET_ID, interval, 2)).willReturn(List.of(
                intervalCandle(start), intervalCandle(start + width)));

        given().queryParam("limit", 2)
                .when().get(PATH, ASSET_ID, interval)
                .then()
                .apply(document("candles-recent-" + interval + "m",
                        resource(baseResource()
                                .responseSchema(schema("RecentMinuteCandlesResponse"))
                                .responseFields(
                                        fieldWithPath("assetId").type(JsonFieldType.STRING).description("자산 UUID"),
                                        fieldWithPath("intervalMinutes").type(JsonFieldType.NUMBER).description("봉 간격(분). 1, 2, 5, 15, 60, 240, 1440"),
                                        fieldWithPath("candles").type(JsonFieldType.ARRAY).description("시작 시간 오름차순의 확정 봉 목록"),
                                        fieldWithPath("candles[].openTimeUnixMilli").type(JsonFieldType.NUMBER).description("봉 시작 시간. UTC Unix millisecond"),
                                        fieldWithPath("candles[].openPrice").type(JsonFieldType.STRING).description("시가. 정밀도 보존 문자열"),
                                        fieldWithPath("candles[].highPrice").type(JsonFieldType.STRING).description("고가"),
                                        fieldWithPath("candles[].lowPrice").type(JsonFieldType.STRING).description("저가"),
                                        fieldWithPath("candles[].closePrice").type(JsonFieldType.STRING).description("종가"),
                                        fieldWithPath("candles[].tradeVolume").type(JsonFieldType.STRING).description("해당 봉 누적 거래량"),
                                        fieldWithPath("candles[].tradeAmount").type(JsonFieldType.STRING).description("해당 봉 누적 거래대금"),
                                        fieldWithPath("candles[].lastEventTimeUnixMilli").type(JsonFieldType.NUMBER).description("마지막 반영 이벤트 시간. UTC Unix millisecond")
                                ).build())))
                .statusCode(200)
                .body("assetId", equalTo(ASSET_ID.toString()))
                .body("intervalMinutes", equalTo(interval))
                .body("candles", hasSize(2))
                .body("candles[0].openTimeUnixMilli", equalTo(start))
                .body("candles[1].openTimeUnixMilli", equalTo(start + width))
                .body("candles[0].openPrice", equalTo("100.123456789012"))
                .body("candles[0].tradeVolume", equalTo("0.123456789012345678"));
    }

    @Test
    @DisplayName("개수를 생략하면 1000개를 조회하고 데이터가 없으면 빈 목록을 반환한다")
    void defaultLimitAndEmptyHistory() {
        given(facade.findRecent(ASSET_ID, 1, 1000)).willReturn(List.of());

        given().when().get(PATH, ASSET_ID, 1).then()
                .statusCode(200).body("candles", hasSize(0));
        then(facade).should().findRecent(ASSET_ID, 1, 1000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1001", "text", "1.5"})
    @DisplayName("개수가 정수가 아니거나 1~1000 범위를 벗어나면 400을 반환한다")
    void rejectInvalidLimit(String limit) {
        given().queryParam("limit", limit).when().get(PATH, ASSET_ID, 1).then()
                .statusCode(400).body("code", equalTo("COMMON-400-001"));
        then(facade).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 조회 개수의 오류 응답을 문서화한다")
    void documentInvalidLimit() {
        given().queryParam("limit", 1001).when().get(PATH, ASSET_ID, 1).then()
                .apply(document("candles-recent-invalid-request",
                        resource(baseResource().responseSchema(schema("ApiErrorResponse"))
                                .responseFields(
                                        fieldWithPath("timestamp").description("오류 발생 시각"),
                                        fieldWithPath("status").description("HTTP 상태 코드"),
                                        fieldWithPath("code").description("COMMON-400-001"),
                                        fieldWithPath("message").description("오류 메시지"),
                                        fieldWithPath("path").description("요청 경로"),
                                        fieldWithPath("validationErrors").description("필드 검증 오류 목록"),
                                        fieldWithPath("validationErrors[].field").description("오류 필드"),
                                        fieldWithPath("validationErrors[].reason").description("검증 실패 사유")
                                ).build())))
                .statusCode(400).body("code", equalTo("COMMON-400-001"));
    }

    @Test
    @DisplayName("잘못된 자산 UUID는 400을 반환한다")
    void rejectInvalidAssetId() {
        given().when().get(PATH, "not-a-uuid", 1).then()
                .statusCode(400).body("code", equalTo("COMMON-400-001"));
        then(facade).shouldHaveNoInteractions();
    }

    private ResourceSnippetParametersBuilder baseResource() {
        return ResourceSnippetParameters.builder()
                .tag("캔들")
                .summary("종목·간격별 최근 확정 봉 조회")
                .description("""
                        JWT 쿠키 또는 HTTP Bearer 인증이 필요합니다.
                        assetId는 등록된 종목의 UUID이며 limit은 1~1000, 생략 시 1000입니다.
                        intervalMinutes는 1, 2, 5, 15, 60, 240, 1440만 지원합니다. 기존 /candles/1m도 유지합니다.
                        선택 간격의 확정 봉을 시작 시간 오름차순으로 반환합니다. 진행 봉은 포함하지 않습니다.
                        버킷은 UTC 기준이며 1440분봉은 UTC 00:00(KST 09:00)에 시작합니다.
                        Redis Sorted Set을 우선 조회하고, 없거나 요청보다 적거나 캐시 장애/손상 시 PostgreSQL을 조회합니다.
                        DB 조회 후 빈 시간대만 캐시에 보충하며 Redis 쓰기 실패에도 DB 결과를 반환합니다.
                        데이터가 없으면(미등록 UUID 포함) 200과 빈 candles 목록을 반환합니다.
                        가격/수량/거래대금은 문자열, 시간은 UTC Unix millisecond입니다.
                        초기 차트는 이 API로 구성하고 이후 진행 봉은 /topic/markets/{marketCode}/candles/{intervalMinutes}m으로 갱신합니다.
                        상위 봉은 비동기 저장되며 1분봉 보정 후 재집계될 수 있습니다. 최신 확정 결과는 다시 조회합니다.
                        체결이 없는 시간의 봉을 새로 만들거나 기간 검색/과거 페이지 조회를 지원하지 않습니다.
                        잘못된 UUID, intervalMinutes 및 limit은 COMMON-400-001(400)입니다.
                        """)
                .pathParameters(parameterWithName("assetId").description("자산 UUID"),
                        parameterWithName("intervalMinutes").description("봉 간격(분). 1, 2, 5, 15, 60, 240, 1440"))
                .queryParameters(parameterWithName("limit").optional().description("최근 봉 개수. 1~1000, 기본 1000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "10", "1441", "-1", "text"})
    @DisplayName("지원하지 않는 봉 간격은 조회하지 않고 400을 반환한다")
    void rejectUnsupportedInterval(String interval) {
        given().when().get(PATH, ASSET_ID, interval).then()
                .statusCode(400).body("code", equalTo("COMMON-400-001"));
        then(facade).shouldHaveNoInteractions();
    }

    private MinuteCandleSnapshot intervalCandle(long start) {
        MinuteCandleSnapshot sample = candle(0);
        return new MinuteCandleSnapshot(ASSET_ID, start, sample.openPrice(), sample.highPrice(),
                sample.lowPrice(), sample.closePrice(), sample.tradeVolume(), sample.tradeAmount(), start + 59999);
    }
}
