package com.ssafy.a107.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.a107.strategy.domain.dsl.ComparisonOperator;
import com.ssafy.a107.strategy.domain.dsl.ConditionFilterNode;
import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.GroupFilterNode;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.domain.dsl.ValueOperand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 서버가 실제로 돌려준 응답을 그대로 저장해 두고 우리 DSL 타입으로 읽히는지 본다.
 * 두 서버가 같은 필드명과 허용값을 쓰는지가 이 계층의 유일한 위험이라 손으로 쓴 예시로는 부족하다.
 * 응답 형태가 바뀌면 `ai/` 에서 다시 받아 이 파일을 교체한다.
 */
class AiTranslateResponseContractTest {

    private static final String CAPTURED_RESPONSE = "/ai/translate-response.json";

    @DisplayName("AI 서버 실제 응답이 전략 DSL로 읽힌다")
    @Test
    void readCapturedResponse() throws IOException {
        AiTranslateResponse response = readCaptured();

        assertThat(response.strategy().timeframe()).isEqualTo(Timeframe.ONE_DAY);
        assertThat(response.strategy().ranking().factor()).isEqualTo(FactorType.VOLUME_RATIO);
        assertThat(response.strategy().selection().topN()).isEqualTo(5);

        GroupFilterNode filters = (GroupFilterNode) response.strategy().filters();
        ConditionFilterNode condition = (ConditionFilterNode) filters.children().getFirst();
        assertThat(((FactorOperand) condition.left()).factor()).isEqualTo(FactorType.VOLUME_RATIO);
        assertThat(condition.operator()).isEqualTo(ComparisonOperator.GTE);
        assertThat(((ValueOperand) condition.right()).value()).isEqualByComparingTo("1.5");
    }

    @DisplayName("AI 서버 실제 응답의 meta 필드가 모두 채워진다")
    @Test
    void readCapturedMeta() throws IOException {
        AiTranslateResponse.Meta meta = readCaptured().meta();

        assertThat(meta.attempts()).isNotNull();
        assertThat(meta.elapsedMs()).isNotNull();
        assertThat(meta.model()).isNotBlank();
        assertThat(meta.warnings()).isNotNull();
        assertThat(meta.changes()).isNotNull();
        assertThat(meta.assumptions()).isNotEmpty();
    }

    private AiTranslateResponse readCaptured() throws IOException {
        try (InputStream captured = getClass().getResourceAsStream(CAPTURED_RESPONSE)) {
            assertThat(captured).as("저장된 AI 서버 응답").isNotNull();
            return new ObjectMapper().readValue(captured, AiTranslateResponse.class);
        }
    }
}
