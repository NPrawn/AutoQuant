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
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestFactorPeriodPolicy;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.service.ContestEntryCommandService;
import com.ssafy.a107.contest.service.ContestReadService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class ContestControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID ROUND_ID = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final Instant SUBMISSION_STARTS_AT = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant SUBMISSION_ENDS_AT = Instant.parse("2026-10-16T00:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-17T09:00:00Z");

    @Mock
    private ContestReadService contestReadService;

    @Mock
    private ContestEntryCommandService contestEntryCommandService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        ContestController controller = new ContestController(contestReadService, contestEntryCommandService);
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

    @DisplayName("대회 목록은 회차 상태와 내 제출을 함께 보여 준다")
    @Test
    void getContests() {
        ContestRoundSummary summary = new ContestRoundSummary(round(), ContestDisplayStatus.OPEN, 12, entry());
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
                                .description("회차마다 화면 상태, 제출 기간, 참가 수, 내 제출을 돌려줍니다.")
                                .responseSchema(schema("ContestRoundListResponse"))
                                .responseFields(listResponseFields())
                                .build())
                ))
                .statusCode(HttpStatus.OK.value())
                .body("contests[0].roundId", equalTo(ROUND_ID.toString()))
                .body("contests[0].status", equalTo("OPEN"))
                .body("contests[0].entryCount", equalTo(12))
                .body("contests[0].mySubmission.submissionCount", equalTo(1))
                .body("contests[0].mySubmission.remainingSubmissions", equalTo(4));
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
                fieldWithPath("scoringRules.minimumReturn").type(JsonFieldType.NUMBER).description("실격 수익률 하한"),
                fieldWithPath("scoringRules.maximumDrawdown").type(JsonFieldType.NUMBER).description("실격 최대 낙폭 상한"),
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
