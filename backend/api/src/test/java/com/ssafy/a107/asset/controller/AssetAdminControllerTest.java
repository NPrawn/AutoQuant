package com.ssafy.a107.asset.controller;

import com.epages.restdocs.apispec.ConstrainedFields;
import com.epages.restdocs.apispec.EnumFields;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.a107.asset.domain.AssetTradingStatus;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.domain.MarketDataCollectionStatus;
import com.ssafy.a107.asset.service.AssetRegistrationFacade;
import com.ssafy.a107.asset.service.AssetRegistrationResult;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.common.exception.ApiExceptionHandler;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.response.ValidatableMockMvcResponse;
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
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.Schema.schema;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class AssetAdminControllerTest {

    private static final UUID ASSET_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final String CREATE_ASSET_DESCRIPTION = """
            Upbit에서 실제 거래지원 여부를 검증한 후 종목과 수집 대상을 등록합니다.
            `collectionStatus=ENABLED`는 수집 희망 상태이며 Go Producer의 실제 구독 완료 상태가 아닙니다.
            주요 오류는 요청 검증 실패 400, 미지원 종목 404, 중복 종목 409, 거래소 응답 오류 502/503입니다.
            """;

    @Mock
    private AssetRegistrationFacade assetRegistrationFacade;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        AssetAdminController controller = new AssetAdminController(assetRegistrationFacade);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                objectMapper
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(jsonConverter)
                .apply(documentationConfiguration(restDocumentation))
                .build();
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    @AfterEach
    void tearDown() {
        RestAssuredMockMvc.reset();
    }

    @DisplayName("관리자가 Upbit 종목을 수집 대상으로 등록한다")
    @Test
    void createAsset() {
        ConstrainedFields requestFields = new ConstrainedFields(
                com.ssafy.a107.asset.controller.request.CreateAssetRequest.class
        );
        EnumFields exchangeFields = new EnumFields(Exchange.class);
        EnumFields tradingStatusFields = new EnumFields(AssetTradingStatus.class);
        EnumFields collectionStatusFields = new EnumFields(MarketDataCollectionStatus.class);
        given(assetRegistrationFacade.registerAsset(Exchange.UPBIT, "KRW-BTC"))
                .willReturn(bitcoinRegistrationResult());

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exchange": "UPBIT",
                          "exchangeMarketCode": "KRW-BTC"
                        }
                        """)
                .when()
                .post("/admin/assets")
                .then()
                .apply(document(
                        "assets-create",
                        resource(ResourceSnippetParameters.builder()
                                .tag("자산")
                                .summary("수집 대상 종목 추가")
                                .description(CREATE_ASSET_DESCRIPTION)
                                .requestSchema(schema("CreateAssetRequest"))
                                .responseSchema(schema("AssetResponse"))
                                .requestFields(
                                        exchangeFields.withPath("exchange")
                                                .description("거래소. 현재 UPBIT만 지원"),
                                        requestFields.withPath("exchangeMarketCode")
                                                .type(JsonFieldType.STRING)
                                                .description("1~30자의 대문자 거래소 마켓 코드. 형식: ^[A-Z0-9]+-[A-Z0-9]+$")
                                )
                                .responseFields(
                                        fieldWithPath("assetId")
                                                .type(JsonFieldType.STRING)
                                                .description("자산 UUID"),
                                        exchangeFields.withPath("exchange")
                                                .description("거래소"),
                                        fieldWithPath("exchangeMarketCode")
                                                .type(JsonFieldType.STRING)
                                                .description("거래소 마켓 코드"),
                                        fieldWithPath("baseCurrencyCode")
                                                .type(JsonFieldType.STRING)
                                                .description("거래 대상 통화 코드"),
                                        fieldWithPath("quoteCurrencyCode")
                                                .type(JsonFieldType.STRING)
                                                .description("가격 표시 통화 코드"),
                                        fieldWithPath("koreanName")
                                                .type(JsonFieldType.STRING)
                                                .description("Upbit 기준 한글 종목명"),
                                        fieldWithPath("englishName")
                                                .type(JsonFieldType.STRING)
                                                .description("Upbit 기준 영문 종목명"),
                                        tradingStatusFields.withPath("tradingStatus")
                                                .description("거래지원 상태"),
                                        collectionStatusFields.withPath("collectionStatus")
                                                .description("수집 대상 등록 상태"),
                                        fieldWithPath("createdAt")
                                                .type(JsonFieldType.STRING)
                                                .description("등록 시각")
                                )
                                .build())
                ))
                .statusCode(HttpStatus.CREATED.value())
                .body("assetId", equalTo(ASSET_ID.toString()))
                .body("exchange", equalTo("UPBIT"))
                .body("exchangeMarketCode", equalTo("KRW-BTC"))
                .body("baseCurrencyCode", equalTo("BTC"))
                .body("quoteCurrencyCode", equalTo("KRW"))
                .body("collectionStatus", equalTo("ENABLED"));
    }

    @DisplayName("마켓 코드 형식이 잘못되면 필드 오류를 반환한다")
    @Test
    void rejectInvalidMarketCodeRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exchange": "UPBIT",
                          "exchangeMarketCode": "krw-btc"
                        }
                        """)
                .when()
                .post("/admin/assets")
                .then()
                .apply(document(
                        "assets-create-invalid-request",
                        resource(createAssetErrorResource())
                ))
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .body("code", equalTo("COMMON-400-001"))
                .body("validationErrors[0].field", equalTo("exchangeMarketCode"));
    }

    @DisplayName("이미 등록된 종목이면 충돌 오류를 반환한다")
    @Test
    void rejectDuplicatedAsset() {
        given(assetRegistrationFacade.registerAsset(Exchange.UPBIT, "KRW-BTC"))
                .willThrow(new ApiException(ApiErrorCode.ASSET_ALREADY_EXISTS));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exchange": "UPBIT",
                          "exchangeMarketCode": "KRW-BTC"
                        }
                        """)
                .when()
                .post("/admin/assets")
                .then()
                .apply(document(
                        "assets-create-duplicated",
                        resource(createAssetErrorResource())
                ))
                .statusCode(HttpStatus.CONFLICT.value())
                .body("code", equalTo("ASSET-409-002"));
    }

    @DisplayName("Upbit에서 지원하지 않는 종목이면 찾을 수 없음 오류를 반환한다")
    @Test
    void rejectUnsupportedAsset() {
        given(assetRegistrationFacade.registerAsset(Exchange.UPBIT, "KRW-NONE"))
                .willThrow(new ApiException(ApiErrorCode.ASSET_NOT_SUPPORTED_BY_EXCHANGE));

        requestAsset("KRW-NONE")
                .apply(document(
                        "assets-create-not-supported",
                        resource(createAssetErrorResource())
                ))
                .statusCode(HttpStatus.NOT_FOUND.value())
                .body("code", equalTo("ASSET-404-001"));
    }

    @DisplayName("Upbit 응답을 처리할 수 없으면 잘못된 게이트웨이 오류를 반환한다")
    @Test
    void handleInvalidUpbitResponse() {
        given(assetRegistrationFacade.registerAsset(Exchange.UPBIT, "KRW-BTC"))
                .willThrow(new ApiException(ApiErrorCode.ASSET_EXCHANGE_INVALID_RESPONSE));

        requestAsset("KRW-BTC")
                .apply(document(
                        "assets-create-invalid-upbit-response",
                        resource(createAssetErrorResource())
                ))
                .statusCode(HttpStatus.BAD_GATEWAY.value())
                .body("code", equalTo("ASSET-502-001"));
    }

    @DisplayName("Upbit를 조회할 수 없으면 서비스 일시 장애 오류를 반환한다")
    @Test
    void handleUnavailableUpbit() {
        given(assetRegistrationFacade.registerAsset(Exchange.UPBIT, "KRW-BTC"))
                .willThrow(new ApiException(ApiErrorCode.ASSET_EXCHANGE_UNAVAILABLE));

        requestAsset("KRW-BTC")
                .apply(document(
                        "assets-create-upbit-unavailable",
                        resource(createAssetErrorResource())
                ))
                .statusCode(HttpStatus.SERVICE_UNAVAILABLE.value())
                .body("code", equalTo("ASSET-503-001"));
    }

    private ValidatableMockMvcResponse requestAsset(String exchangeMarketCode) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "exchange": "UPBIT",
                          "exchangeMarketCode": "%s"
                        }
                        """.formatted(exchangeMarketCode))
                .when()
                .post("/admin/assets")
                .then();
    }

    private ResourceSnippetParameters createAssetErrorResource() {
        ConstrainedFields requestFields = new ConstrainedFields(
                com.ssafy.a107.asset.controller.request.CreateAssetRequest.class
        );
        EnumFields exchangeFields = new EnumFields(Exchange.class);

        return ResourceSnippetParameters.builder()
                .tag("자산")
                .summary("수집 대상 종목 추가")
                .description(CREATE_ASSET_DESCRIPTION)
                .requestSchema(schema("CreateAssetRequest"))
                .responseSchema(schema("ApiErrorResponse"))
                .requestFields(
                        exchangeFields.withPath("exchange")
                                .description("거래소. 현재 UPBIT만 지원"),
                        requestFields.withPath("exchangeMarketCode")
                                .type(JsonFieldType.STRING)
                                .description("1~30자의 대문자 거래소 마켓 코드. 형식: ^[A-Z0-9]+-[A-Z0-9]+$")
                )
                .responseFields(
                        fieldWithPath("timestamp")
                                .type(JsonFieldType.STRING)
                                .description("오류 발생 시각"),
                        fieldWithPath("status")
                                .type(JsonFieldType.NUMBER)
                                .description("HTTP 상태 코드"),
                        fieldWithPath("code")
                                .type(JsonFieldType.STRING)
                                .description("안정적인 API 오류 코드"),
                        fieldWithPath("message")
                                .type(JsonFieldType.STRING)
                                .description("사용자에게 노출할 오류 메시지"),
                        fieldWithPath("path")
                                .type(JsonFieldType.STRING)
                                .description("오류가 발생한 요청 경로"),
                        fieldWithPath("validationErrors")
                                .type(JsonFieldType.ARRAY)
                                .description("요청 필드 검증 오류 목록"),
                        fieldWithPath("validationErrors[].field")
                                .type(JsonFieldType.STRING)
                                .description("검증에 실패한 요청 필드")
                                .optional(),
                        fieldWithPath("validationErrors[].reason")
                                .type(JsonFieldType.STRING)
                                .description("필드 검증 실패 사유")
                                .optional()
                )
                .build();
    }

    private AssetRegistrationResult bitcoinRegistrationResult() {
        return new AssetRegistrationResult(
                ASSET_ID,
                Exchange.UPBIT,
                "KRW-BTC",
                "BTC",
                "KRW",
                "비트코인",
                "Bitcoin",
                AssetTradingStatus.TRADING,
                MarketDataCollectionStatus.ENABLED,
                CREATED_AT
        );
    }
}
