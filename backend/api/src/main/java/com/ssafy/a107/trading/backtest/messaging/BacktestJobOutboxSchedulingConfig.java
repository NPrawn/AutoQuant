package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!backtest-worker")
@EnableScheduling
public class BacktestJobOutboxSchedulingConfig {
}
