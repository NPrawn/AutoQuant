package com.ssafy.a107.candle.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.candle.domain.MinuteCandleCoverage;
import com.ssafy.a107.candle.service.CandleCoverageFacade;
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

import java.time.Instant;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.Schema.schema;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class CandleCoverageControllerTest {

    private static final String PATH = "/candles/coverage";
    private static final Instant OLDEST = Instant.parse("2018-01-01T00:00:00Z");
    private static final Instant NEWEST = Instant.parse("2026-09-16T00:00:00Z");

    @Mock
    private CandleCoverageFacade facade;

    @BeforeEach
    void setUp(RestDocumentationContextProvider documentation) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new CandleCoverageController(facade))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .apply(documentationConfiguration(documentation))
                .build());
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @DisplayName("수집 종목이 보유한 봉의 처음과 마지막 시각을 반환하고 문서화한다")
    @Test
    void getCoverage() {
        given(facade.findCoverage(1440)).willReturn(new MinuteCandleCoverage(OLDEST, NEWEST));

        given().queryParam("intervalMinutes", 1440)
                .when().get(PATH)
                .then()
                .apply(document("candles-coverage",
                        resource(baseResource()
                                .responseSchema(schema("CandleCoverageResponse"))
                                .responseFields(
                                        fieldWithPath("intervalMinutes").type(JsonFieldType.NUMBER).description("봉 간격(분). 1, 2, 5, 15, 60, 240, 1440"),
                                        fieldWithPath("start").type(JsonFieldType.STRING).optional().description("보유한 가장 오래된 봉의 시작 시각. 봉이 없으면 null"),
                                        fieldWithPath("end").type(JsonFieldType.STRING).optional().description("보유한 가장 최근 봉의 시작 시각. 봉이 없으면 null")
                                ).build())))
                .statusCode(200)
                .body("intervalMinutes", equalTo(1440))
                .body("start", equalTo("2018-01-01T00:00:00Z"))
                .body("end", equalTo("2026-09-16T00:00:00Z"));
    }

    @DisplayName("간격을 생략하면 일봉 구간을 조회한다")
    @Test
    void defaultsToDailyInterval() {
        given(facade.findCoverage(1440)).willReturn(new MinuteCandleCoverage(OLDEST, NEWEST));

        given().when().get(PATH).then().statusCode(200).body("intervalMinutes", equalTo(1440));

        then(facade).should().findCoverage(1440);
    }

    @DisplayName("봉이 없으면 오류가 아니라 빈 구간을 반환한다")
    @Test
    void emptyCoverageIsNotAnError() {
        given(facade.findCoverage(1440)).willReturn(MinuteCandleCoverage.empty());

        given().when().get(PATH).then()
                .statusCode(200)
                .body("start", nullValue())
                .body("end", nullValue());
    }

    @DisplayName("지원하지 않는 봉 간격은 조회하지 않고 400을 반환한다")
    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "10", "1441", "-1", "text"})
    void rejectUnsupportedInterval(String intervalMinutes) {
        given().queryParam("intervalMinutes", intervalMinutes)
                .when().get(PATH)
                .then()
                .statusCode(400).body("code", equalTo("COMMON-400-001"));

        then(facade).shouldHaveNoInteractions();
    }

    private ResourceSnippetParametersBuilder baseResource() {
        return ResourceSnippetParameters.builder()
                .tag("캔들")
                .summary("수집 종목의 봉 보유 구간 조회")
                .description("""
                        JWT 쿠키 또는 HTTP Bearer 인증이 필요합니다.
                        수집 중인 종목 전체를 합쳐 해당 간격으로 보유한 가장 오래된 봉과 가장 최근 봉의 시작 시각을 반환합니다.
                        대상 종목은 백테스트 후보군과 같은 기준이라 화면이 고른 기간을 엔진이 그대로 평가할 수 있습니다.
                        상장이 이른 종목이 start를, 수집이 살아 있는 종목이 end를 정합니다.
                        intervalMinutes는 1, 2, 5, 15, 60, 240, 1440만 지원하며 생략하면 1440입니다.
                        봉이 하나도 없으면 200과 함께 start·end가 null입니다. 조회 실패가 아닙니다.
                        시각은 UTC ISO-8601 문자열이며 1440분봉은 UTC 00:00(KST 09:00)에 시작합니다.
                        지원하지 않는 intervalMinutes는 COMMON-400-001(400)입니다.
                        """)
                .queryParameters(parameterWithName("intervalMinutes").optional()
                        .description("봉 간격(분). 1, 2, 5, 15, 60, 240, 1440. 기본 1440"));
    }
}
