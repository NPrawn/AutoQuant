package com.ssafy.a107.notification.controller.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/*
 * 화면에서 고른 알림들. 본문 없이 부르면 전체 삭제라, 빈 목록을 보내 전체가 지워지는 일이 없게 비어 있으면 거절한다.
 */
public record DeleteNotificationsRequest(
        @NotEmpty List<Long> notificationIds
) {
}
