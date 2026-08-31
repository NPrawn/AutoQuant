package com.ssafy.a107.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/* 타임아웃을 두지 않으면 SSAFY 지연이 그대로 로그인 요청 지연이 된다. */
@Configuration
public class SsafyRestClientConfig {

    @Bean
    public RestClient ssafyRestClient(RestClient.Builder builder, SsafyOAuth2Properties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return builder.requestFactory(requestFactory).build();
    }
}
