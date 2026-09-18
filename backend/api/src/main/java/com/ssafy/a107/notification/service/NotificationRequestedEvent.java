package com.ssafy.a107.notification.service;

import com.ssafy.a107.notification.domain.NotificationType;

import java.util.Objects;
import java.util.UUID;

/*
 * 알릴 일이 생겼다는 사실만 담는다. 실제 저장은 커밋 뒤에 한다.
 * 알림이 먼저 남고 원래 트랜잭션이 되돌아가면 일어나지 않은 일을 알리게 된다.
 */
public record NotificationRequestedEvent(
        UUID memberId,
        NotificationType notificationType,
        String title,
        String body,
        String targetId
) {

    public NotificationRequestedEvent {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(notificationType);
        Objects.requireNonNull(title);
    }
}
