package com.ssafy.a107.trading.backtest.messaging;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "backtest.job")
public record BacktestJobQueueProperties(
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotBlank String routingKey,
        @NotBlank String deadLetterExchange,
        @NotBlank String deadLetterQueue,
        @NotBlank String deadLetterRoutingKey
) {
}
