package com.ssafy.a107.strategy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import com.ssafy.a107.strategy.domain.dsl.Rebalance;
import com.ssafy.a107.strategy.domain.dsl.RebalancePeriod;
import com.ssafy.a107.strategy.domain.dsl.Selection;
import com.ssafy.a107.strategy.domain.dsl.StrategyDsl;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import com.ssafy.a107.strategy.service.StrategyCommandService;
import com.ssafy.a107.strategy.service.StrategyReadService;
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
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StrategyControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Long STRATEGY_ID = 1L;
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Mock
    private StrategyCommandService strategyCommandService;

    @Mock
    private StrategyReadService strategyReadService;

    @BeforeEach
    void setUp() {
        StrategyController controller = new StrategyController(strategyCommandService, strategyReadService);
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

    @DisplayName("로그인 사용자가 유효한 DSL로 Strategy를 생성한다")
    @Test
    void createStrategy() {
        org.mockito.BDDMockito.given(strategyCommandService.createStrategy(
                eq(MEMBER_ID),
                eq("RSI 전략"),
                eq("설명"),
                any(StrategyDsl.class)
        ))
                .willReturn(strategy());

        given()
                .contentType(ContentType.JSON)
                .body(validCreateRequest())
                .when()
                .post("/strategies")
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body("strategyId", equalTo(STRATEGY_ID.intValue()))
                .body("name", equalTo("RSI 전략"))
                .body("strategyDsl.filters.type", equalTo("CONDITION"));
    }

    @DisplayName("유효하지 않은 DSL 요청은 생성 전에 400으로 거부한다")
    @Test
    void rejectInvalidStrategyDsl() {
        given()
                .contentType(ContentType.JSON)
                .body(validCreateRequest().replace("\"topN\": 5", "\"topN\": 0"))
                .when()
                .post("/strategies")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON-400-001"))
                .body("validationErrors[0].field", equalTo("strategyDsl.selection.topN"));

        then(strategyCommandService).shouldHaveNoInteractions();
    }

    @DisplayName("로그인 사용자의 활성 Strategy 목록을 조회한다")
    @Test
    void getStrategies() {
        org.mockito.BDDMockito.given(strategyReadService.getStrategies(MEMBER_ID)).willReturn(List.of(strategy()));

        given()
                .when()
                .get("/strategies")
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("[0].strategyId", equalTo(STRATEGY_ID.intValue()));
    }

    @DisplayName("로그인 사용자가 소유한 Strategy를 단건 조회한다")
    @Test
    void getStrategy() {
        org.mockito.BDDMockito.given(strategyReadService.getStrategy(MEMBER_ID, STRATEGY_ID)).willReturn(strategy());

        given()
                .when()
                .get("/strategies/{strategyId}", STRATEGY_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("strategyId", equalTo(STRATEGY_ID.intValue()));
    }

    @DisplayName("로그인 사용자가 소유한 Strategy를 soft delete 한다")
    @Test
    void deleteStrategy() {
        given()
                .when()
                .delete("/strategies/{strategyId}", STRATEGY_ID)
                .then()
                .statusCode(HttpStatus.NO_CONTENT.value());

        then(strategyCommandService).should().deleteStrategy(MEMBER_ID, STRATEGY_ID);
    }

    @DisplayName("전략을 공개로 바꾼다")
    @Test
    void changeVisibility() {
        Strategy published = strategy();
        published.changeVisibility(true);
        org.mockito.BDDMockito.given(strategyCommandService.changeVisibility(MEMBER_ID, STRATEGY_ID, true))
                .willReturn(published);

        given()
                .contentType(ContentType.JSON)
                .body("{\"published\": true}")
                .when()
                .patch("/strategies/{strategyId}/visibility", STRATEGY_ID)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("published", equalTo(true));
    }

    @DisplayName("공개 여부를 주지 않으면 400으로 거부한다")
    @Test
    void rejectVisibilityWithoutValue() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .patch("/strategies/{strategyId}/visibility", STRATEGY_ID)
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON-400-001"));

        then(strategyCommandService).shouldHaveNoInteractions();
    }

    @DisplayName("전략을 복제하면 201과 새 전략을 반환한다")
    @Test
    void copyStrategy() {
        org.mockito.BDDMockito.given(strategyCommandService.copyStrategy(MEMBER_ID, STRATEGY_ID))
                .willReturn(strategy());

        given()
                .when()
                .post("/strategies/{strategyId}/copy", STRATEGY_ID)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body("published", equalTo(false));
    }

    private Strategy strategy() {
        Strategy strategy = new Strategy(
                new Member(LoginProvider.SSAFY, "ssafy-member-107", "strategy@example.com", "전략 사용자"),
                "RSI 전략",
                "설명",
                strategyDsl()
        );
        ReflectionTestUtils.setField(strategy, "strategyId", STRATEGY_ID);
        ReflectionTestUtils.setField(strategy, "createdAt", CREATED_AT);
        return strategy;
    }

    private StrategyDsl strategyDsl() {
        return new StrategyDsl(
                Timeframe.ONE_HOUR,
                new ConditionFilterNode(
                        new FactorOperand(FactorType.RSI, 14),
                        ComparisonOperator.LT,
                        new ValueOperand(BigDecimal.valueOf(30))
                ),
                new Ranking(FactorType.RETURN, 20, RankingDirection.DESC),
                new Selection(5),
                new Rebalance(RebalancePeriod.WEEKLY)
        );
    }

    private String validCreateRequest() {
        return """
                {
                  "name": "RSI 전략",
                  "description": "설명",
                  "strategyDsl": {
                    "timeframe": "1H",
                    "filters": {
                      "type": "CONDITION",
                      "left": { "type": "FACTOR", "factor": "RSI", "period": 14 },
                      "operator": "LT",
                      "right": { "type": "VALUE", "value": 30 }
                    },
                    "ranking": { "factor": "RETURN", "period": 20, "direction": "DESC" },
                    "selection": { "topN": 5 },
                    "rebalance": { "period": "WEEKLY" }
                  }
                }
                """;
    }
}
