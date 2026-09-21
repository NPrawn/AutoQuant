package com.ssafy.a107.notification.controller.response;

import com.ssafy.a107.notification.domain.NotificationType;

import java.util.List;
import java.util.Map;

/* 알림 종류 네 가지가 열거형 순서대로 모두 들어 있다. 행이 없는 종류는 켜진 것으로 온다. */
public record NotificationSettingsResponse(List<NotificationSettingResponse> settings) {

    public record NotificationSettingResponse(NotificationType notificationType, boolean enabled) {
    }

    public static NotificationSettingsResponse from(Map<NotificationType, Boolean> preferences) {
        return new NotificationSettingsResponse(preferences.entrySet()
                .stream()
                .map(entry -> new NotificationSettingResponse(entry.getKey(), entry.getValue()))
                .toList());
    }
}
