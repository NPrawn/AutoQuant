package com.ssafy.a107.trading.backtest.controller.request;

import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.backtest.service.CreateBacktestRunCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateBacktestRunRequest(
        @NotNull(message = "전략 ID는 필수입니다.")
        @Positive(message = "전략 ID는 양수여야 합니다.")
        Long strategyId,

        @NotNull(message = "백테스트 시작 시각은 필수입니다.")
        Instant periodStart,

        @NotNull(message = "백테스트 종료 시각은 필수입니다.")
        Instant periodEnd,

        @NotBlank(message = "기준 통화는 필수입니다.")
        @Pattern(regexp = "[A-Za-z]{3,10}", message = "기준 통화 형식이 올바르지 않습니다.")
        String quoteCurrency,

        @NotNull(message = "초기 자본은 필수입니다.")
        @DecimalMin(value = "0.00000001", message = "초기 자본은 0보다 커야 합니다.")
        @Digits(integer = 16, fraction = 8, message = "초기 자본의 정밀도가 올바르지 않습니다.")
        BigDecimal initialCapital,

        @NotNull(message = "수수료율은 필수입니다.")
        @DecimalMin(value = "0", message = "수수료율은 음수일 수 없습니다.")
        @Digits(integer = 4, fraction = 8, message = "수수료율의 정밀도가 올바르지 않습니다.")
        BigDecimal feeRate,

        @NotNull(message = "슬리피지는 필수입니다.")
        @DecimalMin(value = "0", message = "슬리피지는 음수일 수 없습니다.")
        @Digits(integer = 8, fraction = 4, message = "슬리피지의 정밀도가 올바르지 않습니다.")
        BigDecimal slippageBps,

        @NotNull(message = "체결가 규칙은 필수입니다.")
        ExecutionPriceRule executionPriceRule,

        @NotBlank(message = "엔진 버전은 필수입니다.")
        @Size(max = 50, message = "엔진 버전은 50자 이하여야 합니다.")
        String engineVersion,

        @NotBlank(message = "시장 데이터 버전은 필수입니다.")
        @Size(max = 50, message = "시장 데이터 버전은 50자 이하여야 합니다.")
        String marketDataVersion
) {

    public CreateBacktestRunCommand toCommand() {
        return new CreateBacktestRunCommand(
                strategyId,
                periodStart,
                periodEnd,
                quoteCurrency,
                initialCapital,
                feeRate,
                slippageBps,
                executionPriceRule,
                engineVersion,
                marketDataVersion
        );
    }
}
