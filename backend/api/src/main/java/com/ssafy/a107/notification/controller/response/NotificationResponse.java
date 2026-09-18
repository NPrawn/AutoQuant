package com.ssafy.a107.notification.controller.response;

import com.ssafy.a107.notification.domain.Notification;
import com.ssafy.a107.notification.domain.NotificationType;

import java.time.Instant;

/*
 * 어디로 갈지는 화면이 정한다. 서버는 종류와 대상 식별자만 주고 경로를 만들지 않는다.
 * 경로 문자열은 프론트의 routes 하나가 소유한다.
 */
public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String body,
        String targetId,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
