package com.ssafy.a107.realtime.service;

import com.ssafy.a107.realtime.domain.LiveMinuteCandle;
import com.ssafy.a107.realtime.domain.RealtimeTrade;

public interface RealtimeEventPublisher {

    void publish(RealtimeTrade trade);

    void publish(LiveMinuteCandle candle);
}
