package com.ssafy.a107.realtime.infrastructure.websocket;

import com.ssafy.a107.realtime.controller.response.LiveMinuteCandleResponse;
import com.ssafy.a107.realtime.controller.response.RealtimeTradeResponse;
import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.realtime.domain.RealtimeTrade;
import com.ssafy.a107.realtime.service.RealtimeEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompRealtimeEventPublisher implements RealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(RealtimeTrade trade) {
        messagingTemplate.convertAndSend(RealtimeDestinations.trades(trade.marketCode()),
                RealtimeTradeResponse.from(trade));
    }

    @Override
    public void publish(LiveMinuteCandle candle) {
        messagingTemplate.convertAndSend(RealtimeDestinations.liveMinuteCandles(candle.marketCode(), candle.intervalMinutes()),
                LiveMinuteCandleResponse.from(candle));
    }
}
