package com.ssafy.a107.notification.controller.response;

/* 상단 배지가 자주 묻는 값이라 목록과 따로 둔다. */
public record UnreadNotificationCountResponse(long unreadCount) {
}
