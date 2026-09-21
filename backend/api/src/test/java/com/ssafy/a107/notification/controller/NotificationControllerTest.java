package com.ssafy.a107.notification.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import com.ssafy.a107.notification.domain.Notification;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.service.NotificationCommandService;
import com.ssafy.a107.notification.service.NotificationPreferenceService;
import com.ssafy.a107.notification.service.NotificationReadService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class NotificationControllerTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BACKTEST_ID = "2bfb9f4e-6994-4d3d-90cd-6250bf5ab2b1";

    @Mock
    private NotificationReadService notificationReadService;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider documentation) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RestAssuredMockMvc.mockMvc(MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationReadService, notificationCommandService, notificationPreferenceService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .apply(documentationConfiguration(documentation))
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
        SecurityContextHolder.clearContext();
    }

    @DisplayName("내 알림을 최신순으로 반환하고 문서화한다")
    @Test
    void getNotifications() {
        /* 목을 준비하는 일이 다른 스터빙 안에서 일어나면 Mockito 가 스터빙이 끝나지 않았다고 본다. */
        List<Notification> notifications = List.of(notification());
        given(notificationReadService.getNotifications(MEMBER_ID)).willReturn(notifications);

        given().when().get("/notifications")
                .then()
                .apply(document("notifications-list",
                        resource(baseResource()
                                .responseSchema(schema("NotificationResponse"))
                                .responseFields(
                                        fieldWithPath("[].notificationId").type(JsonFieldType.NUMBER).description("알림 식별자"),
                                        fieldWithPath("[].notificationType").type(JsonFieldType.STRING).description("알림 종류. BACKTEST_COMPLETED, BACKTEST_FAILED, SIMULATION_FAILED"),
                                        fieldWithPath("[].title").type(JsonFieldType.STRING).description("한 줄 제목"),
                                        fieldWithPath("[].body").type(JsonFieldType.STRING).optional().description("본문. 없으면 null"),
                                        fieldWithPath("[].targetId").type(JsonFieldType.STRING).optional().description("알림이 가리키는 대상 식별자. 종류마다 대상이 다르다"),
                                        fieldWithPath("[].readAt").type(JsonFieldType.STRING).optional().description("읽은 시각. 안 읽었으면 null"),
                                        fieldWithPath("[].createdAt").type(JsonFieldType.STRING).description("알림이 생긴 시각")
                                ).build())))
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].notificationType", equalTo("BACKTEST_COMPLETED"))
                .body("[0].targetId", equalTo(BACKTEST_ID))
                .body("[0].readAt", nullValue());
    }

    @DisplayName("알림 설정은 네 종류를 모두 돌려준다")
    @Test
    void getSettings() {
        Map<NotificationType, Boolean> preferences = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            preferences.put(type, true);
        }
        preferences.put(NotificationType.BACKTEST_FAILED, false);
        given(notificationPreferenceService.getPreferences(MEMBER_ID)).willReturn(preferences);

        given().when().get("/notifications/settings")
                .then()
                .statusCode(200)
                .body("settings", hasSize(NotificationType.values().length))
                .body("settings.find { it.notificationType == 'BACKTEST_FAILED' }.enabled", equalTo(false))
                .body("settings.find { it.notificationType == 'BACKTEST_COMPLETED' }.enabled", equalTo(true));
    }

    @DisplayName("알림 설정을 바꾸면 바뀐 종류만 서비스에 넘기고 전체를 돌려준다")
    @Test
    void updateSettings() {
        Map<NotificationType, Boolean> preferences = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            preferences.put(type, true);
        }
        preferences.put(NotificationType.CONTEST_SCORED, false);
        given(notificationPreferenceService.updatePreferences(MEMBER_ID, Map.of(NotificationType.CONTEST_SCORED, false)))
                .willReturn(preferences);

        given()
                .contentType("application/json")
                .body("{\"settings\":[{\"notificationType\":\"CONTEST_SCORED\",\"enabled\":false}]}")
                .when().put("/notifications/settings")
                .then()
                .statusCode(200)
                .body("settings", hasSize(NotificationType.values().length))
                .body("settings.find { it.notificationType == 'CONTEST_SCORED' }.enabled", equalTo(false));
    }

    @DisplayName("빈 알림 설정 요청은 400 으로 거부한다")
    @Test
    void rejectEmptySettings() {
        given()
                .contentType("application/json")
                .body("{\"settings\":[]}")
                .when().put("/notifications/settings")
                .then()
                .statusCode(400);

        then(notificationPreferenceService).shouldHaveNoInteractions();
    }

    @DisplayName("안 읽은 알림 수를 반환하고 문서화한다")
    @Test
    void getUnreadCount() {
        given(notificationReadService.countUnread(MEMBER_ID)).willReturn(3L);

        given().when().get("/notifications/unread-count")
                .then()
                .apply(document("notifications-unread-count",
                        resource(baseResource()
                                .responseSchema(schema("UnreadNotificationCountResponse"))
                                .responseFields(
                                        fieldWithPath("unreadCount").type(JsonFieldType.NUMBER).description("안 읽은 알림 수")
                                ).build())))
                .statusCode(200)
                .body("unreadCount", equalTo(3));
    }

    @DisplayName("알림 한 건을 읽음 처리하고 문서화한다")
    @Test
    void markRead() {
        given().when().post("/notifications/{notificationId}/read", 7)
                .then()
                .apply(document("notifications-read",
                        resource(baseResource()
                                .pathParameters(parameterWithName("notificationId").description("알림 식별자"))
                                .build())))
                .statusCode(204);

        then(notificationCommandService).should().markRead(MEMBER_ID, 7L);
    }

    @DisplayName("남의 알림을 읽으려 하면 404를 반환한다")
    @Test
    void rejectMarkReadOfOtherMember() {
        willThrow(new ApiException(ApiErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationCommandService).markRead(MEMBER_ID, 7L);

        given().when().post("/notifications/{notificationId}/read", 7)
                .then()
                .statusCode(404)
                .body("code", equalTo("NOTIFICATION-404-001"));
    }

    @DisplayName("모두 읽음을 처리하고 문서화한다")
    @Test
    void markAllRead() {
        given().when().post("/notifications/read-all")
                .then()
                .apply(document("notifications-read-all", resource(baseResource().build())))
                .statusCode(204);

        then(notificationCommandService).should().markAllRead(MEMBER_ID);
    }

    /* 식별자와 생성 시각은 DB 가 채우므로 시험에서는 저장된 뒤의 모습을 흉내 낸다. */
    private Notification notification() {
        Notification notification = mock(Notification.class);
        given(notification.getNotificationId()).willReturn(7L);
        given(notification.getNotificationType()).willReturn(NotificationType.BACKTEST_COMPLETED);
        given(notification.getTitle()).willReturn("이평선 골든크로스 백테스트가 끝났습니다");
        given(notification.getBody()).willReturn("평가 기간 2024-01-01 ~ 2024-06-30. 결과를 확인할 수 있습니다.");
        given(notification.getTargetId()).willReturn(BACKTEST_ID);
        given(notification.getReadAt()).willReturn(null);
        given(notification.getCreatedAt()).willReturn(Instant.parse("2026-09-16T00:00:00Z"));
        return notification;
    }

    private ResourceSnippetParametersBuilder baseResource() {
        return ResourceSnippetParameters.builder()
                .tag("알림")
                .summary("알림 조회와 읽음 처리")
                .description("""
                        JWT 쿠키 또는 HTTP Bearer 인증이 필요합니다. 언제나 내 알림만 다룹니다.
                        알림은 백테스트 완료, 백테스트 실패, 모의투자 자동 중단 세 가지에서 생깁니다.
                        사용자가 직접 멈춘 모의투자는 이미 아는 일이라 알리지 않습니다.
                        목록은 최신순 최대 100건입니다. 넘치는 옛 알림은 목록에서 빠지지만 안 읽은 수에는 그대로 셉니다.
                        targetId가 가리키는 대상은 종류마다 다르며 이동 경로는 화면이 정합니다.
                        남의 알림을 읽거나 읽음 처리하면 NOTIFICATION-404-001(404)입니다.
                        """);
    }

}
