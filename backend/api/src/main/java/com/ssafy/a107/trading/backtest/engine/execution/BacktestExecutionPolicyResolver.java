package com.ssafy.a107.trading.backtest.engine.execution;

import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@EnableConfigurationProperties(BacktestExecutionPolicyProperties.class)
public class BacktestExecutionPolicyResolver {

    private final BacktestExecutionPolicyProperties properties;

    public BacktestExecutionPolicyResolver(
            BacktestExecutionPolicyProperties properties
    ) {
        this.properties = Objects.requireNonNull(properties);
    }

    public VirtualExecutionPolicy resolve(BacktestRun backtestRun) {
        Objects.requireNonNull(backtestRun);

        return new VirtualExecutionPolicy(
                backtestRun.getFeeRate(),
                backtestRun.getSlippageBps(),
                properties.minimumOrderAmount(),
                properties.quantityStepSize()
        );
    }
}
