package com.ssafy.a107.contest.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.contest.domain.ContestDisplayStatus;
import com.ssafy.a107.contest.domain.ContestDisqualificationReason;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.MarketDataset;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestEquityPoint;
import com.ssafy.a107.contest.domain.ContestFactorPeriodPolicy;
import com.ssafy.a107.contest.domain.ContestRankingSort;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestRankingRow;
import com.ssafy.a107.contest.repository.ContestRoundTopResult;
import com.ssafy.a107.contest.service.ContestEntryCommandService;
import com.ssafy.a107.contest.service.ContestReadService;
import com.ssafy.a107.contest.service.ContestResultReadService;
import com.ssafy.a107.contest.service.ContestRoundDetail;
import com.ssafy.a107.contest.service.ContestRoundSummary;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.Schema.schema;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class ContestControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("2a1d0f2e-6d63-4a1f-9d27-0a0d3f0d9a11");
    private static final UUID ROUND_ID = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final Instant SUBMISSION_STARTS_AT = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant SUBMISSION_ENDS_AT = Instant.parse("2026-10-16T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-17T09:00:00Z");

    @Mock
    private ContestReadService contestReadService;

    @Mock
    private ContestEntryCommandService contestEntryCommandService;

    @Mock
    private ContestResultReadService contestResultReadService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        ContestController controller = new ContestController(
                contestReadService, contestEntryCommandService, contestResultReadService);
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
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new MemberPrincipal(MEMBER_ID), null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        io.restassured.module.mockmvc.RestAssuredMockMvc.reset();
        SecurityContextHolder.clearContext();
    }

    @DisplayName("대회 목록은 회차 상태와 내 제출, 내 순위와 1위를 함께 보여 준다")
    @Test
    void getContests() {
        ContestResult myResult = ContestResult.scored(
                UUID.randomUUID(),
                new BigDecimal("78.667"),
                new BigDecimal("18.800"),
                new BigDecimal("17.000"),
                new BigDecimal("18.200"),
                new BigDecimal("17.600"),
                new BigDecimal("48.133"),
                2
        );
        ContestRoundTopResult top = new ContestRoundTopResult(
                ROUND_ID, "변동성 눌림 스윙", "가온누리", new BigDecimal("55.180"), OTHER_MEMBER_ID);
        ContestRoundSummary summary = new ContestRoundSummary(
                round(), ContestDisplayStatus.OPEN, 12, entry(), myResult, top);
        given(contestReadService.getRounds(MEMBER_ID)).willReturn(List.of(summary));

        given()
                .when()
                .get("/contests")
                .then()
                .apply(document(
                        "contests-list",
                        resource(ResourceSnippetParameters.builder()
                                .tag("대회")
                                .summary("대회 회차 목록 조회")
                                .description("""
                                        회차마다 화면 상태, 제출 기간, 참가 수, 내 제출을 돌려줍니다.
                                        채점된 제출이 있으면 내 순위·총점과 회차 1위도 함께 옵니다.
                                        점수는 절대값이라 마감 전에도 값이 있고, 그때 순위는 잠정입니다.
                                        1위가 다른 참가자면 이름을 첫 글자와 마지막 글자만 남기고 가립니다.
                                        """)
                                .responseSchema(schema("ContestRoundListResponse"))
                                .responseFields(listResponseFields())
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("contests[0].roundId", equalTo(ROUND_ID.toString()))
                .body("contests[0].status", equalTo("OPEN"))
                .body("contests[0].entryCount", equalTo(12))
                .body("contests[0].mySubmission.submissionCount", equalTo(1))
                .body("contests[0].mySubmission.remainingSubmissions", equalTo(4))
                .body("contests[0].myRanking", equalTo(2))
                .body("contests[0].myTotalScore", equalTo(48.133f))
                .body("contests[0].topStrategyName", equalTo("변동성 눌림 스윙"))
                .body("contests[0].topNickname", equalTo("가**리"))
                .body("contests[0].topTotalScore", equalTo(55.18f));
    }

    @DisplayName("제출하지 않았거나 채점 전인 회차는 내 순위와 1위가 비어 있다")
    @Test
    void leaveRankingEmptyBeforeScoring() {
        ContestRoundSummary summary = new ContestRoundSummary(
                round(), ContestDisplayStatus.OPEN, 0, null, null, null);
        given(contestReadService.getRounds(MEMBER_ID)).willReturn(List.of(summary));

        given()
                .when()
                .get("/contests")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("contests[0].mySubmission", equalTo(null))
                .body("contests[0].myRanking", equalTo(null))
                .body("contests[0].topStrategyName", equalTo(null));
    }

    @DisplayName("대회 상세는 채점 규칙 상수와 팩터 기간 상한을 함께 보여 준다")
    @Test
    void getContest() {
        ContestRoundDetail detail = new ContestRoundDetail(
                round(), ContestDisplayStatus.OPEN, 12, entry(), ContestFactorPeriodPolicy.caps(5));
        given(contestReadService.getRound(MEMBER_ID, ROUND_ID)).willReturn(detail);

        given()
                .when()
                .get("/contests/{roundId}", ROUND_ID)
                .then()
                .apply(document(
                        "contests-get",
                        resource(ResourceSnippetParameters.builder()
                                .tag("대회")
                                .summary("대회 회차 상세 조회")
                                .description("제출 규칙과 채점 규칙 상수, 내 제출과 남은 교체 횟수를 돌려줍니다.")
                                .responseSchema(schema("ContestRoundDetailResponse"))
                                .responseFields(detailResponseFields())
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("maxSubmissions", equalTo(5))
                .body("factorPeriodCaps.1H", equalTo(839))
                .body("factorPeriodCaps.4H", equalTo(209))
                .body("factorPeriodCaps.1D", equalTo(34))
                .body("scoringRules.returnWeight", equalTo(0.5f))
                .body("mySubmission.strategyName", equalTo("모멘텀 전략"));
    }

    @DisplayName("전략을 제출하면 제출 횟수와 남은 횟수를 돌려준다")
    @Test
    void submitEntry() {
        ContestEntry entry = entry();
        given(contestEntryCommandService.submit(MEMBER_ID, ROUND_ID, 7L)).willReturn(entry);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("strategyId", 7))
                .when()
                .put("/contests/{roundId}/entry", ROUND_ID)
                .then()
                .apply(document(
                        "contests-submit-entry",
                        resource(ResourceSnippetParameters.builder()
                                .tag("대회")
                                .summary("대회 전략 제출과 교체")
                                .description("제출 기간 안에서 본인의 전략을 제출하거나 교체합니다. 같은 전략을 다시 제출해도 횟수에 넣습니다.")
                                .requestSchema(schema("SubmitContestEntryRequest"))
                                .responseSchema(schema("ContestEntryResponse"))
                                .requestFields(fieldWithPath("strategyId").type(JsonFieldType.NUMBER)
                                        .description("제출할 전략 ID"))
                                .responseFields(entryResponseFields())
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("strategyId", equalTo(7))
                .body("submissionCount", equalTo(1))
                .body("remainingSubmissions", equalTo(4));
    }

    @DisplayName("제출 기간이 아니면 409를 돌려준다")
    @Test
    void rejectSubmissionOutsideThePeriod() {
        willThrow(new ApiException(ApiErrorCode.CONTEST_SUBMISSION_CLOSED))
                .given(contestEntryCommandService).submit(MEMBER_ID, ROUND_ID, 7L);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("strategyId", 7))
                .when()
                .put("/contests/{roundId}/entry", ROUND_ID)
                .then()
                .statusCode(HttpStatus.CONFLICT.value())
                .body("code", equalTo("CONTEST-409-001"));
    }

    @DisplayName("팩터 기간이 워밍업보다 길면 400을 돌려준다")
    @Test
    void rejectFactorPeriodLongerThanWarmup() {
        willThrow(new ApiException(ApiErrorCode.CONTEST_FACTOR_PERIOD_TOO_LONG))
                .given(contestEntryCommandService).submit(MEMBER_ID, ROUND_ID, 7L);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("strategyId", 7))
                .when()
                .put("/contests/{roundId}/entry", ROUND_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("CONTEST-400-001"));
    }

    @DisplayName("채점 중에는 끝난 백테스트 수를 돌려준다")
    @Test
    void getProgress() {
        given(contestResultReadService.getProgress(ROUND_ID))
                .willReturn(new ContestResultReadService.ContestProgress(ContestDisplayStatus.SCORING, 24, 40));

        given()
                .when()
                .get("/contests/{roundId}/progress", ROUND_ID)
                .then()
                .apply(document(
                        "contests-progress",
                        resource(ResourceSnippetParameters.builder()
                                .tag("대회")
                                .summary("대회 채점 진행 조회")
                                .description("채점 중 회차의 끝난 백테스트 수와 전체 수를 돌려줍니다.")
                                .responseSchema(schema("ContestProgressResponse"))
                                .responseFields(
                                        fieldWithPath("status").type(JsonFieldType.STRING).description("화면 상태"),
                                        fieldWithPath("finishedBacktests").type(JsonFieldType.NUMBER)
                                                .description("끝난 백테스트 수"),
                                        fieldWithPath("totalBacktests").type(JsonFieldType.NUMBER)
                                                .description("전체 백테스트 수")
                                )
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("status", equalTo("SCORING"))
                .body("finishedBacktests", equalTo(24))
                .body("totalBacktests", equalTo(40));
    }

    @DisplayName("종료된 회차는 순위표와 실격 제출을 함께 돌려준다")
    @Test
    void getResults() {
        List<ContestResultReadService.ContestRanking> rankings = List.of(
                new ContestResultReadService.ContestRanking(1, MEMBER_ID, "대회 참가자", "모멘텀 전략",
                        new BigDecimal("73.333"), new BigDecimal("66.667"), new BigDecimal("80.000"), null, null),
                new ContestResultReadService.ContestRanking(null, OTHER_MEMBER_ID, "다른 참가자", "몰빵 전략",
                        null, null, null, ContestDisqualificationReason.LOW_EXPOSURE, MarketDatasetKind.GENERAL)
        );
        given(contestResultReadService.getResults(ROUND_ID)).willReturn(rankings);

        given()
                .when()
                .get("/contests/{roundId}/results", ROUND_ID)
                .then()
                .apply(document(
                        "contests-results",
                        resource(ResourceSnippetParameters.builder()
                                .tag("대회")
                                .summary("대회 순위표 조회")
                                .description("""
                                        회차의 순위와 점수를 돌려줍니다. 실격 제출은 순위 없이 사유와 함께 옵니다.
                                        마감 전 순위는 그때까지 채점된 제출 사이의 잠정 순위입니다.
                                        다른 참가자 이름은 첫 글자와 마지막 글자만 남기고 가립니다.
                                        """)
                                .responseSchema(schema("ContestResultsResponse"))
                                .responseFields(
                                        fieldWithPath("rankings[].ranking").type(JsonFieldType.NUMBER).optional()
                                                .description("순위. 실격이면 null"),
                                        fieldWithPath("rankings[].nickname").type(JsonFieldType.STRING)
                                                .description("참가자 이름. 다른 참가자는 가린 이름"),
                                        fieldWithPath("rankings[].strategyName").type(JsonFieldType.STRING).description("전략 이름"),
                                        fieldWithPath("rankings[].totalScore").type(JsonFieldType.NUMBER).optional()
                                                .description("총점"),
                                        fieldWithPath("rankings[].returnScore").type(JsonFieldType.NUMBER).optional()
                                                .description("수익률 점수"),
                                        fieldWithPath("rankings[].stabilityScore").type(JsonFieldType.NUMBER).optional()
                                                .description("안정성 점수"),
                                        fieldWithPath("rankings[].disqualificationReason").type(JsonFieldType.STRING)
                                                .optional().description("실격 사유"),
                                        fieldWithPath("rankings[].disqualifiedKind").type(JsonFieldType.STRING)
                                                .optional().description("실격이 걸린 데이터셋")
                                )
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("rankings[0].ranking", equalTo(1))
                .body("rankings[0].totalScore", equalTo(73.333f))
                .body("rankings[1].disqualificationReason", equalTo("LOW_EXPOSURE"));
    }

    @DisplayName("내 결과는 데이터셋별 성적과 점수 계산 과정을 돌려준다")
    @Test
    void getMyResult() {
        ContestResult result = ContestResult.scored(
                UUID.randomUUID(),
                new BigDecimal("66.667"),
                new BigDecimal("100.000"),
                new BigDecimal("66.667"),
                new BigDecimal("100.000"),
                new BigDecimal("80.000"),
                new BigDecimal("73.333"),
                1
        );
        ContestResultReadService.ContestDatasetOutcome outcome = new ContestResultReadService.ContestDatasetOutcome(
                MarketDatasetKind.GENERAL,
                Instant.parse("2030-02-11T00:00:00Z"),
                Instant.parse("2030-05-13T00:00:00Z"),
                new BigDecimal("1.549"),
                new BigDecimal("0.38448381"),
                new BigDecimal("1.721"),
                new BigDecimal("0.189"),
                new BigDecimal("0.989"),
                40,
                List.of(new ContestEquityPoint(Instant.parse("2030-02-12T00:00:00Z"), new BigDecimal("0.0125")))
        );
        given(contestResultReadService.getMyResult(MEMBER_ID, ROUND_ID)).willReturn(
                new ContestResultReadService.ContestMyResult(
                        result, 7L, "모멘텀 전략", List.of(outcome), 10, new BigDecimal("73.333"), 4, 4));

        given()
                .when()
                .get("/contests/{roundId}/results/me", ROUND_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("strategyId", equalTo(7))
                .body("strategyName", equalTo("모멘텀 전략"))
                .body("ranking", equalTo(1))
                .body("stabilityScore", equalTo(80.0f))
                .body("entryCount", equalTo(10))
                .body("datasets[0].kind", equalTo("GENERAL"))
                .body("datasets[0].marketReturn", equalTo(1.549f))
                .body("datasets[0].marketMaximumDrawdown", equalTo(0.38448381f))
                .body("datasets[0].averageExposure", equalTo(0.989f))
                .body("datasets[0].equityCurve[0].valuationAt", equalTo("2030-02-12T00:00:00Z"))
                .body("datasets[0].equityCurve[0].returnRate", equalTo(0.0125f));
    }

    @DisplayName("종료된 회차는 데이터셋의 시드와 뽑은 주를 공개한다")
    @Test
    void getDatasets() {
        MarketDataset dataset = new MarketDataset(
                round(),
                MarketDatasetKind.GENERAL,
                Instant.parse("2030-02-11T00:00:00Z"),
                Instant.parse("2030-05-13T00:00:00Z"),
                new BigDecimal("1.549"),
                new BigDecimal("0.38448381"),
                new BigDecimal("0.38448381"),
                new BigDecimal("0.32298406"),
                Instant.parse("2019-10-07T00:00:00Z"),
                Instant.parse("2021-10-04T00:00:00Z"),
                20260915L,
                "[{\"source_start\": \"2019-12-30T00:00:00Z\", \"label\": \"FLAT\"}]",
                "contest-2026-09-15",
                302
        );
        given(contestResultReadService.getDatasets(ROUND_ID)).willReturn(List.of(dataset));

        given()
                .when()
                .get("/contests/{roundId}/datasets", ROUND_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("datasets[0].kind", equalTo("GENERAL"))
                .body("datasets[0].seed", equalTo(20260915))
                .body("datasets[0].filledHours", equalTo(302));
    }

    @DisplayName("랭킹은 조건을 그대로 서비스에 넘기고 내 제출을 표시한다")
    @Test
    void getRankings() {
        ContestRankingRow mine = row(1, 7L, "모멘텀 전략", "대회 참가자", true, MEMBER_ID);
        ContestRankingRow other = row(2, 9L, "추세 전략", "다른 참가자", false, OTHER_MEMBER_ID);
        Page<ContestRankingRow> page = new PageImpl<>(List.of(mine, other), PageRequest.of(0, 20), 2);
        given(contestResultReadService.getRankings(
                eq(ROUND_ID), eq(ContestRankingSort.STABILITY), eq(MarketDatasetKind.BEAR), eq("모멘텀"), any()))
                .willReturn(new ContestResultReadService.ContestRankings(page, 6));

        given()
                .queryParam("sort", "STABILITY")
                .queryParam("kind", "BEAR")
                .queryParam("query", "모멘텀")
                .when()
                .get("/contests/{roundId}/rankings", ROUND_ID)
                .then()
                .apply(document(
                        "contests-rankings",
                        resource(ResourceSnippetParameters.builder()
                                .tag("대회")
                                .summary("대회 회차 랭킹 조회")
                                .description("""
                                        회차의 순위를 페이지로 돌려줍니다. 마감 전 순위는 잠정입니다.
                                        정렬은 총점(TOTAL)과 안정성(STABILITY)이고, 국면 필터는 그 국면 점수가
                                        세 국면 중 가장 높은 제출만 남깁니다. 일반장(GENERAL)은 필터 대상이 아니라 400입니다.
                                        검색은 전략 이름의 부분 일치입니다. 다른 참가자 이름은 가려서 주므로 검색하지 않습니다.
                                        실격 제출은 순위가 없어 목록에서 빠지고 수만 disqualifiedCount 로 옵니다.
                                        """)
                                .responseSchema(schema("ContestRankingPageResponse"))
                                .queryParameters(
                                        parameterWithName("sort").optional().description("정렬 기준. TOTAL(기본), STABILITY"),
                                        parameterWithName("kind").optional().description("국면 필터. BULL, BEAR, SIDEWAYS"),
                                        parameterWithName("query").optional().description("전략 이름 검색어"),
                                        parameterWithName("page").optional().description("페이지 번호. 기본 0"),
                                        parameterWithName("size").optional().description("페이지 크기. 1~100, 기본 20")
                                )
                                .responseFields(rankingResponseFields())
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("content[0].strategyId", equalTo(7))
                .body("content[0].mine", equalTo(true))
                .body("content[0].nickname", equalTo("대회 참가자"))
                .body("content[1].nickname", equalTo("다****자"))
                .body("content[0].published", equalTo(true))
                .body("content[0].bearRegimeScore", equalTo(66.667f))
                .body("content[1].mine", equalTo(false))
                .body("totalElements", equalTo(2))
                .body("disqualifiedCount", equalTo(6));
    }

    @DisplayName("일반장은 국면 필터로 쓸 수 없다")
    @Test
    void rejectGeneralAsRegimeFilter() {
        given()
                .queryParam("kind", "GENERAL")
                .when()
                .get("/contests/{roundId}/rankings", ROUND_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON-400-001"));
    }

    @DisplayName("채점이 끝나기 전에는 랭킹도 공개하지 않는다")
    @Test
    void hideRankingsBeforeScoringFinishes() {
        willThrow(new ApiException(ApiErrorCode.CONTEST_RESULT_NOT_PUBLISHED))
                .given(contestResultReadService).getRankings(eq(ROUND_ID), any(), any(), any(), any());

        given()
                .when()
                .get("/contests/{roundId}/rankings", ROUND_ID)
                .then()
                .statusCode(HttpStatus.CONFLICT.value())
                .body("code", equalTo("CONTEST-409-003"));
    }

    @DisplayName("채점이 끝나기 전에는 결과를 공개하지 않는다")
    @Test
    void hideResultsBeforeScoringFinishes() {
        willThrow(new ApiException(ApiErrorCode.CONTEST_RESULT_NOT_PUBLISHED))
                .given(contestResultReadService).getResults(ROUND_ID);

        given()
                .when()
                .get("/contests/{roundId}/results", ROUND_ID)
                .then()
                .statusCode(HttpStatus.CONFLICT.value())
                .body("code", equalTo("CONTEST-409-003"));
    }

    private ContestRound round() {
        ContestRound round = new ContestRound("1회차 모의 대회", SUBMISSION_STARTS_AT, SUBMISSION_ENDS_AT, 5, 5, 13);
        ReflectionTestUtils.setField(round, "roundId", ROUND_ID);
        return round;
    }

    private ContestEntry entry() {
        Strategy strategy = mock(Strategy.class);
        given(strategy.getStrategyId()).willReturn(7L);
        given(strategy.getName()).willReturn("모멘텀 전략");

        Member member = new Member(LoginProvider.SSAFY, "contest-member-107", "contest@example.com", "대회 참가자");
        ContestEntry entry = new ContestEntry(round(), member, strategy, SUBMITTED_AT);
        ReflectionTestUtils.setField(entry, "lastSubmittedAt", SUBMITTED_AT.plus(Duration.ofMinutes(30)));
        return entry;
    }

    private ContestRankingRow row(int ranking, long strategyId, String strategyName, String nickname,
                                 boolean published, UUID memberId) {
        return new ContestRankingRow(
                ranking,
                strategyId,
                strategyName,
                nickname,
                published,
                new BigDecimal("73.333"),
                new BigDecimal("66.667"),
                new BigDecimal("80.000"),
                new BigDecimal("100.000"),
                new BigDecimal("66.667"),
                new BigDecimal("100.000"),
                memberId
        );
    }

    private FieldDescriptor[] rankingResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("content[].ranking").type(JsonFieldType.NUMBER).description("순위"),
                fieldWithPath("content[].strategyId").type(JsonFieldType.NUMBER).description("전략 ID"),
                fieldWithPath("content[].strategyName").type(JsonFieldType.STRING).description("전략 이름"),
                fieldWithPath("content[].nickname").type(JsonFieldType.STRING)
                        .description("참가자 이름. 다른 참가자는 첫 글자와 마지막 글자만 남기고 가린다"),
                fieldWithPath("content[].published").type(JsonFieldType.BOOLEAN)
                        .description("공개 전략 여부. 공개 전략만 복제할 수 있다"),
                fieldWithPath("content[].totalScore").type(JsonFieldType.NUMBER).description("종합 점수"),
                fieldWithPath("content[].returnScore").type(JsonFieldType.NUMBER).description("수익률 점수"),
                fieldWithPath("content[].stabilityScore").type(JsonFieldType.NUMBER).description("안정성 점수"),
                fieldWithPath("content[].bullRegimeScore").type(JsonFieldType.NUMBER).description("상승장 국면 점수"),
                fieldWithPath("content[].bearRegimeScore").type(JsonFieldType.NUMBER).description("하락장 국면 점수"),
                fieldWithPath("content[].sidewaysRegimeScore").type(JsonFieldType.NUMBER)
                        .description("횡보장 국면 점수"),
                fieldWithPath("content[].mine").type(JsonFieldType.BOOLEAN).description("내 제출 여부"),
                fieldWithPath("page").type(JsonFieldType.NUMBER).description("페이지 번호"),
                fieldWithPath("size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                fieldWithPath("totalElements").type(JsonFieldType.NUMBER).description("순위에 오른 제출 수"),
                fieldWithPath("totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                fieldWithPath("disqualifiedCount").type(JsonFieldType.NUMBER).description("실격 제출 수"),
        };
    }

    private FieldDescriptor[] listResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("contests[].roundId").type(JsonFieldType.STRING).description("회차 ID"),
                fieldWithPath("contests[].title").type(JsonFieldType.STRING).description("회차 이름"),
                fieldWithPath("contests[].status").type(JsonFieldType.STRING)
                        .description("화면 상태 (SCHEDULED, OPEN, SCORING, FINISHED)"),
                fieldWithPath("contests[].submissionStartsAt").type(JsonFieldType.STRING).description("제출 시작 시각"),
                fieldWithPath("contests[].submissionEndsAt").type(JsonFieldType.STRING).description("제출 마감 시각"),
                fieldWithPath("contests[].entryCount").type(JsonFieldType.NUMBER).description("참가 수"),
                fieldWithPath("contests[].mySubmission").type(JsonFieldType.OBJECT).optional()
                        .description("내 제출. 제출하지 않았으면 null"),
                fieldWithPath("contests[].mySubmission.strategyId").type(JsonFieldType.NUMBER).description("제출 전략 ID"),
                fieldWithPath("contests[].mySubmission.strategyName").type(JsonFieldType.STRING).description("제출 전략 이름"),
                fieldWithPath("contests[].mySubmission.submissionCount").type(JsonFieldType.NUMBER).description("제출 횟수"),
                fieldWithPath("contests[].mySubmission.remainingSubmissions").type(JsonFieldType.NUMBER)
                        .description("남은 제출 횟수"),
                fieldWithPath("contests[].mySubmission.firstSubmittedAt").type(JsonFieldType.STRING).description("첫 제출 시각"),
                fieldWithPath("contests[].mySubmission.lastSubmittedAt").type(JsonFieldType.STRING).description("마지막 제출 시각"),
                fieldWithPath("contests[].myRanking").type(JsonFieldType.NUMBER).optional()
                        .description("내 순위. 제출하지 않았거나 채점 전이거나 실격이면 null"),
                fieldWithPath("contests[].myTotalScore").type(JsonFieldType.NUMBER).optional()
                        .description("내 총점. 내 순위와 같은 조건으로 null"),
                fieldWithPath("contests[].topStrategyName").type(JsonFieldType.STRING).optional()
                        .description("1위 전략 이름. 채점된 제출이 없으면 null"),
                fieldWithPath("contests[].topNickname").type(JsonFieldType.STRING).optional()
                        .description("1위 참가자 이름"),
                fieldWithPath("contests[].topTotalScore").type(JsonFieldType.NUMBER).optional()
                        .description("1위 총점"),
        };
    }

    private FieldDescriptor[] detailResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("roundId").type(JsonFieldType.STRING).description("회차 ID"),
                fieldWithPath("title").type(JsonFieldType.STRING).description("회차 이름"),
                fieldWithPath("status").type(JsonFieldType.STRING).description("화면 상태"),
                fieldWithPath("submissionStartsAt").type(JsonFieldType.STRING).description("제출 시작 시각"),
                fieldWithPath("submissionEndsAt").type(JsonFieldType.STRING).description("제출 마감 시각"),
                fieldWithPath("maxSubmissions").type(JsonFieldType.NUMBER).description("제출과 교체를 합친 한도"),
                fieldWithPath("warmupWeeks").type(JsonFieldType.NUMBER).description("대회 데이터 워밍업 주 수"),
                fieldWithPath("evaluationWeeks").type(JsonFieldType.NUMBER).description("평가 주 수"),
                fieldWithPath("entryCount").type(JsonFieldType.NUMBER).description("참가 수"),
                fieldWithPath("factorPeriodCaps").type(JsonFieldType.OBJECT).description("timeframe 별 팩터 기간 상한"),
                fieldWithPath("factorPeriodCaps.1H").type(JsonFieldType.NUMBER).description("1시간봉 상한"),
                fieldWithPath("factorPeriodCaps.4H").type(JsonFieldType.NUMBER).description("4시간봉 상한"),
                fieldWithPath("factorPeriodCaps.1D").type(JsonFieldType.NUMBER).description("일봉 상한"),
                fieldWithPath("scoringRules.returnWeight").type(JsonFieldType.NUMBER).description("수익률 점수 가중치"),
                fieldWithPath("scoringRules.stabilityWeight").type(JsonFieldType.NUMBER).description("안정성 점수 가중치"),
                fieldWithPath("scoringRules.minimumExposure").type(JsonFieldType.NUMBER).description("실격 평균 투자 비중 하한"),
                fieldWithPath("scoringRules.initialCapital").type(JsonFieldType.NUMBER).description("초기 자본"),
                fieldWithPath("scoringRules.feeRate").type(JsonFieldType.NUMBER).description("수수료율"),
                fieldWithPath("scoringRules.slippageBps").type(JsonFieldType.NUMBER).description("슬리피지(bps)"),
                fieldWithPath("mySubmission").type(JsonFieldType.OBJECT).optional()
                        .description("내 제출. 제출하지 않았으면 null"),
                fieldWithPath("mySubmission.strategyId").type(JsonFieldType.NUMBER).description("제출 전략 ID"),
                fieldWithPath("mySubmission.strategyName").type(JsonFieldType.STRING).description("제출 전략 이름"),
                fieldWithPath("mySubmission.submissionCount").type(JsonFieldType.NUMBER).description("제출 횟수"),
                fieldWithPath("mySubmission.remainingSubmissions").type(JsonFieldType.NUMBER).description("남은 제출 횟수"),
                fieldWithPath("mySubmission.firstSubmittedAt").type(JsonFieldType.STRING).description("첫 제출 시각"),
                fieldWithPath("mySubmission.lastSubmittedAt").type(JsonFieldType.STRING).description("마지막 제출 시각"),
        };
    }

    private FieldDescriptor[] entryResponseFields() {
        return new FieldDescriptor[]{
                fieldWithPath("roundId").type(JsonFieldType.STRING).description("회차 ID"),
                fieldWithPath("strategyId").type(JsonFieldType.NUMBER).description("제출 전략 ID"),
                fieldWithPath("strategyName").type(JsonFieldType.STRING).description("제출 전략 이름"),
                fieldWithPath("submissionCount").type(JsonFieldType.NUMBER).description("제출 횟수"),
                fieldWithPath("remainingSubmissions").type(JsonFieldType.NUMBER).description("남은 제출 횟수"),
                fieldWithPath("firstSubmittedAt").type(JsonFieldType.STRING).description("첫 제출 시각"),
                fieldWithPath("lastSubmittedAt").type(JsonFieldType.STRING).description("마지막 제출 시각"),
        };
    }
}
