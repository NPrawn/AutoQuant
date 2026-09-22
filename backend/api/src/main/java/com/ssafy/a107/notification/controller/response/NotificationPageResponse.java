package com.ssafy.a107.notification.controller.response;

import com.ssafy.a107.notification.domain.Notification;
import org.springframework.data.domain.Page;

import java.util.List;

/*
 * 알림 화면의 한 장. 알림은 지우기 전까지 계속 쌓여 상한을 두고 끊으면 옛 알림을 볼 방법이 없다.
 * 페이지 모양은 랭킹(ContestRankingPageResponse)과 같게 둔다.
 */
public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static NotificationPageResponse from(Page<Notification> notifications) {
        return new NotificationPageResponse(
                notifications.getContent().stream().map(NotificationResponse::from).toList(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getTotalElements(),
                notifications.getTotalPages()
        );
    }
}
