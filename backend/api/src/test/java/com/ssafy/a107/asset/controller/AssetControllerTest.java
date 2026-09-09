package com.ssafy.a107.asset.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.service.AssetReadService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.Schema.schema;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class AssetControllerTest {
    @Mock
    private AssetReadService service;

    @BeforeEach
    void setUp(RestDocumentationContextProvider documentation) {
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.standaloneSetup(new AssetController(service))
                .apply(documentationConfiguration(documentation)).build());
    }

    @AfterEach
    void tearDown() { RestAssuredMockMvc.reset(); }

    @Test
    @DisplayName("차트 종목의 UUID와 구독 코드 및 이름을 반환한다")
    void listChartAssets() {
        Asset bitcoin = new Asset(Exchange.UPBIT, "KRW-BTC", "BTC", "KRW", "비트코인", "Bitcoin", null);
        UUID assetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ReflectionTestUtils.setField(bitcoin, "assetId", assetId);
        given(service.findChartAssets()).willReturn(List.of(bitcoin));

        RestAssuredMockMvc.given().when().get("/api/assets").then()
                .apply(document("assets-chart-list", resource(ResourceSnippetParameters.builder()
                        .tag("자산").summary("차트 종목 목록 조회")
                        .description("JWT 인증 필요. 등록된 Upbit KRW 종목 중 TRADING 상태를 마켓 코드 오름차순으로 반환합니다. 수집 연결 또는 봉 존재를 보장하지 않습니다. 없으면 빈 배열입니다.")
                        .responseSchema(schema("ChartAssetsResponse"))
                        .responseFields(
                                fieldWithPath("[].assetId").type(JsonFieldType.STRING).description("봉 조회에 사용하는 종목 UUID"),
                                fieldWithPath("[].marketCode").type(JsonFieldType.STRING).description("STOMP 구독에 사용하는 마켓 코드"),
                                fieldWithPath("[].koreanName").type(JsonFieldType.STRING).description("종목 한글 이름")
                        ).build())))
                .statusCode(200).body("[0].assetId", equalTo(assetId.toString()))
                .body("[0].marketCode", equalTo("KRW-BTC")).body("[0].koreanName", equalTo("비트코인"));
    }

    @Test
    @DisplayName("등록된 차트 종목이 없으면 빈 배열을 반환한다")
    void emptyAssets() {
        given(service.findChartAssets()).willReturn(List.of());
        RestAssuredMockMvc.given().when().get("/api/assets").then().statusCode(200).body("", hasSize(0));
    }
}
