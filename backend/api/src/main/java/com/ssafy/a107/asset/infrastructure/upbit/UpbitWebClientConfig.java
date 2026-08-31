package com.ssafy.a107.asset.infrastructure.upbit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(UpbitProperties.class)
public class UpbitWebClientConfig {

    @Bean
    @Qualifier("upbitWebClient")
    public WebClient upbitWebClient(UpbitProperties properties) {
        return WebClient.create(properties.baseUrl());
    }
}
