package com.ssafy.a107.trading.backtest.engine.execution;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "backtest.execution")
public record BacktestExecutionPolicyProperties(
        @NotNull @DecimalMin(value = "0", inclusive = false)
        BigDecimal minimumOrderAmount,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        BigDecimal quantityStepSize
) {
}
