package com.ssafy.a107.auth.infrastructure.ssafy;

import com.ssafy.a107.auth.config.SsafyOAuth2Properties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(SsafyOAuth2Properties.class)
public class SsafyWebClientConfig {

    @Bean
    @Qualifier("ssafyWebClient")
    public WebClient ssafyWebClient(SsafyOAuth2Properties properties) {
        return WebClient.create(properties.baseUrl());
    }
}
