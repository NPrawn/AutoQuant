package com.ssafy.a107.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.ai.service.AiStrategyTranslationService;
import com.ssafy.a107.ai.service.port.AiStrategyTranslation;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Logic;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
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

import java.util.List;
import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AiStrategyControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");

    @Mock
    private AiStrategyTranslationService aiStrategyTranslationService;

    @BeforeEach
    void setUp() {
        AiStrategyController controller = new AiStrategyController(aiStrategyTranslationService);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        RestAssuredMockMvc.mockMvc(mockMvc);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID),
                null,
                List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
        SecurityContextHolder.clearContext();
    }

    @DisplayName("로그인 사용자의 전략 설명을 전략 초안으로 바꾼다")
    @Test
    void translateStrategy() {
        org.mockito.BDDMockito.given(aiStrategyTranslationService.translate(
                eq(MEMBER_ID),
                eq("5% 이상 오른 코인 사줘"),
                isNull()
        )).willReturn(translation(strategyDsl()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "userInput": "5% 이상 오른 코인 사줘"
                        }
                        """)
                .when()
                .post("/ai/strategies/translate")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("strategy.timeframe", equalTo("1D"))
                .body("strategy.filters.children[0].operator", equalTo("GTE"))
                .body("meta.attempts", equalTo(1))
                .body("meta.assumptions[0]", equalTo("기본 시간프레임 1D를 사용했습니다."));
    }

    @DisplayName("현재 전략을 함께 보내면 편집 요청으로 넘긴다")
    @Test
    void translateEditRequest() {
        org.mockito.BDDMockito.given(aiStrategyTranslationService.translate(
                eq(MEMBER_ID),
                eq("1시간봉으로 바꿔줘"),
                any(StrategyDsl.class)
        )).willReturn(translation(strategyDsl()));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "userInput": "1시간봉으로 바꿔줘",
                          "currentStrategy": {
                            "timeframe": "1D",
                            "filters": {
                              "type": "GROUP",
                              "logic": "AND",
                              "children": [
                                {
                                  "type": "CONDITION",
                                  "left": {"type": "FACTOR", "factor": "RETURN", "period": 20},
                                  "operator": "GTE",
                                  "right": {"type": "VALUE", "value": 5}
                                }
                              ]
                            },
                            "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
                            "selection": {"topN": 5},
                            "rebalance": {"period": "WEEKLY"}
                          }
                        }
                        """)
                .when()
                .post("/ai/strategies/translate")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("meta.attempts", equalTo(1));
    }

    @DisplayName("전략을 만들지 않은 응답도 200으로 돌려준다")
    @Test
    void returnEmptyStrategyAsSuccess() {
        org.mockito.BDDMockito.given(aiStrategyTranslationService.translate(
                eq(MEMBER_ID),
                eq("안녕하세요"),
                isNull()
        )).willReturn(new AiStrategyTranslation(
                null,
                0,
                "gpt-5.4-mini",
                1L,
                List.of(),
                List.of("입력이 짧아 전략을 만들지 않았습니다."),
                List.of()
        ));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "userInput": "안녕하세요"
                        }
                        """)
                .when()
                .post("/ai/strategies/translate")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("strategy", nullValue())
                .body("meta.attempts", equalTo(0))
                .body("meta.assumptions[0]", equalTo("입력이 짧아 전략을 만들지 않았습니다."));
    }

    @DisplayName("전략 설명이 비면 AI 서버를 부르지 않고 400으로 거부한다")
    @Test
    void rejectBlankUserInput() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "userInput": "  "
                        }
                        """)
                .when()
                .post("/ai/strategies/translate")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON-400-001"))
                .body("validationErrors[0].field", equalTo("userInput"));

        then(aiStrategyTranslationService).shouldHaveNoInteractions();
    }

    @DisplayName("현재 전략이 DSL을 어기면 AI 서버를 부르지 않고 400으로 거부한다")
    @Test
    void rejectInvalidCurrentStrategy() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "userInput": "1시간봉으로 바꿔줘",
                          "currentStrategy": {
                            "timeframe": "1D",
                            "filters": {
                              "type": "GROUP",
                              "logic": "AND",
                              "children": [
                                {
                                  "type": "CONDITION",
                                  "left": {"type": "FACTOR", "factor": "RETURN", "period": 20},
                                  "operator": "GTE",
                                  "right": {"type": "VALUE", "value": 5}
                                }
                              ]
                            },
                            "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
                            "selection": {"topN": 0},
                            "rebalance": {"period": "WEEKLY"}
                          }
                        }
                        """)
                .when()
                .post("/ai/strategies/translate")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON-400-001"))
                .body("validationErrors[0].field", equalTo("currentStrategy.selection.topN"));

        then(aiStrategyTranslationService).shouldHaveNoInteractions();
    }

    @DisplayName("회원 호출 한도를 넘으면 429를 백엔드 오류 형식으로 돌려준다")
    @Test
    void returnQuotaExceededInBackendErrorFormat() {
        org.mockito.BDDMockito.given(aiStrategyTranslationService.translate(
                eq(MEMBER_ID),
                eq("5% 이상 오른 코인 사줘"),
                isNull()
        )).willThrow(new ApiException(ApiErrorCode.AI_TRANSLATION_QUOTA_EXCEEDED));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "userInput": "5% 이상 오른 코인 사줘"
                        }
                        """)
                .when()
                .post("/ai/strategies/translate")
                .then()
                .statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
                .body("code", equalTo("AI-429-001"))
                .body("path", equalTo("/ai/strategies/translate"));
    }

    private AiStrategyTranslation translation(StrategyDsl strategy) {
        return new AiStrategyTranslation(
                strategy,
                1,
                "gpt-5.4-mini",
                1_984L,
                List.of(),
                List.of("기본 시간프레임 1D를 사용했습니다."),
                List.of()
        );
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_DAY,
                new GroupFilterNode(
                        Logic.AND,
                        List.of(new ConditionFilterNode(
                                new FactorOperand(FactorType.RETURN, 20),
                                ComparisonOperator.GTE,
                                new ValueOperand(java.math.BigDecimal.valueOf(5))
                        ))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }
}
