package com.ssafy.a107.notification.controller.request;

import com.ssafy.a107.notification.domain.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/* 바꿀 종류만 보낸다. 같은 종류가 두 번 오면 뒤의 값이 이긴다. */
public record UpdateNotificationSettingsRequest(
        @NotEmpty(message = "바꿀 알림 설정이 하나 이상 필요합니다.")
        List<@Valid Setting> settings
) {

    public record Setting(
            @NotNull(message = "알림 종류는 필수입니다.")
            NotificationType notificationType,

            @NotNull(message = "켬·끔 값은 필수입니다.")
            Boolean enabled
    ) {
    }

    public Map<NotificationType, Boolean> toChanges() {
        Map<NotificationType, Boolean> changes = new EnumMap<>(NotificationType.class);
        settings.forEach(setting -> changes.put(setting.notificationType(), setting.enabled()));
        return changes;
    }
}
