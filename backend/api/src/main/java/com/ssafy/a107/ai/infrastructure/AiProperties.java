package com.ssafy.a107.ai.infrastructure;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        @NotBlank
        String baseUrl,

        /* AI 서버 실측 p95가 4.6초다. 재시도 한 번을 포함해도 들어오도록 10초를 준다. */
        @NotNull
        Duration timeout,

        @Positive
        int translationsPerMinutePerMember
) {

    @AssertTrue(message = "AI timeout은 0보다 커야 합니다.")
    public boolean isTimeoutPositive() {
        return timeout == null || (!timeout.isZero() && !timeout.isNegative());
    }
}
