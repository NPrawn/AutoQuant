package com.ssafy.a107.trading.simulation.controller.request;

import com.ssafy.a107.trading.backtest.domain.ExecutionPriceRule;
import com.ssafy.a107.trading.simulation.service.CreateSimulationRunCommand;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateSimulationRunRequest(
        @NotNull @Positive Long strategyId,
        @NotNull @Digits(integer = 16, fraction = 8) BigDecimal initialCapital,
        @NotNull @Digits(integer = 4, fraction = 8) BigDecimal feeRate,
        @NotNull @Digits(integer = 8, fraction = 4) BigDecimal slippageBps,
        @NotNull ExecutionPriceRule executionPriceRule,
        @NotBlank @Size(max = 50) String engineVersion
) {
    public CreateSimulationRunCommand toCommand() {
        return new CreateSimulationRunCommand(
                strategyId, initialCapital, feeRate, slippageBps, executionPriceRule, engineVersion
        );
    }
}
