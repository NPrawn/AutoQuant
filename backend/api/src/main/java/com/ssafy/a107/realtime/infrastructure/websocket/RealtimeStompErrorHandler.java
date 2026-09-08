package com.ssafy.a107.realtime.infrastructure.websocket;

import com.ssafy.a107.common.exception.ApiErrorCode;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

@Component
public class RealtimeStompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable exception) {
        // 기본 오류 문자열에 클라이언트 프레임과 내부 예외가 섞여 나가지 않게 제한한다.
        ApiErrorCode errorCode = ApiErrorCode.REALTIME_INVALID_STOMP_REQUEST;
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setMessage(errorCode.getCode());
        return MessageBuilder.createMessage(errorCode.getMessage().getBytes(StandardCharsets.UTF_8),
                headers.getMessageHeaders());
    }
}
