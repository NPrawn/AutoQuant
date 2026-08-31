package com.ssafy.a107.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/* 시간을 주입받아 테스트에서 고정할 수 있게 한다. 모든 시각은 UTC 로 다룬다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
