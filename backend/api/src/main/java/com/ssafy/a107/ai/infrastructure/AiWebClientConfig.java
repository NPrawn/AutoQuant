package com.ssafy.a107.ai.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiWebClientConfig {

    @Bean
    @Qualifier("aiWebClient")
    public WebClient aiWebClient(AiProperties properties) {
        return WebClient.create(properties.baseUrl());
    }
}
