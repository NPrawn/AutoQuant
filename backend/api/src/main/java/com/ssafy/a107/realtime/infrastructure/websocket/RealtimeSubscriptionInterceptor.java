package com.ssafy.a107.realtime.infrastructure.websocket;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSubscriptionInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null
                || !(headers.getUser() instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ApiException(ApiErrorCode.REALTIME_INVALID_STOMP_REQUEST);
        }
        if (headers.getMessageType() == SimpMessageType.HEARTBEAT) {
            return message;
        }
        if (headers.getCommand() == null) {
            throw new ApiException(ApiErrorCode.REALTIME_INVALID_STOMP_REQUEST);
        }
        return switch (headers.getCommand()) {
            case CONNECT, STOMP, DISCONNECT, UNSUBSCRIBE -> message;
            case SUBSCRIBE -> {
                if (!RealtimeDestinations.isAllowed(headers.getDestination())) {
                    throw new ApiException(ApiErrorCode.REALTIME_INVALID_STOMP_REQUEST);
                }
                yield message;
            }
            default -> throw new ApiException(ApiErrorCode.REALTIME_INVALID_STOMP_REQUEST);
        };
    }
}
