package com.ssafy.a107.asset.infrastructure.upbit;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "upbit")
public record UpbitProperties(
        @NotBlank
        String baseUrl,

        @NotNull
        Duration timeout
) {

    @AssertTrue(message = "Upbit timeout은 0보다 커야 합니다.")
    public boolean isTimeoutPositive() {
        return timeout == null || (!timeout.isZero() && !timeout.isNegative());
    }
}
