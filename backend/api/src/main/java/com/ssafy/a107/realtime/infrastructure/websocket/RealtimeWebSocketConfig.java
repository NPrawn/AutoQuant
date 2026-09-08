package com.ssafy.a107.realtime.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final RealtimeSubscriptionInterceptor subscriptionInterceptor;
    private final RealtimeStompErrorHandler errorHandler;

    public RealtimeWebSocketConfig(
            RealtimeSubscriptionInterceptor subscriptionInterceptor,
            RealtimeStompErrorHandler errorHandler
    ) {
        this.subscriptionInterceptor = subscriptionInterceptor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws"); // JWT HTTP 인증 및 기본 same-origin 정책을 유지한다.
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionInterceptor);
        // 구독/해제 순서를 유지하면서 preSend 거부는 STOMP ERROR 처리 경로로 전파한다.
        registration.taskExecutor().corePoolSize(1).maxPoolSize(1);
    }
}
