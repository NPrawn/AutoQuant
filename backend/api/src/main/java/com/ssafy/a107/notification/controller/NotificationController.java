package com.ssafy.a107.notification.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.notification.controller.response.NotificationResponse;
import com.ssafy.a107.notification.controller.response.UnreadNotificationCountResponse;
import com.ssafy.a107.notification.service.NotificationCommandService;
import com.ssafy.a107.notification.service.NotificationReadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationReadService notificationReadService;
    private final NotificationCommandService notificationCommandService;

    public NotificationController(
            NotificationReadService notificationReadService,
            NotificationCommandService notificationCommandService
    ) {
        this.notificationReadService = notificationReadService;
        this.notificationCommandService = notificationCommandService;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(@AuthenticationPrincipal MemberPrincipal principal) {
        return notificationReadService.getNotifications(principal.memberId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse getUnreadCount(@AuthenticationPrincipal MemberPrincipal principal) {
        return new UnreadNotificationCountResponse(notificationReadService.countUnread(principal.memberId()));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long notificationId
    ) {
        notificationCommandService.markRead(principal.memberId(), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationCommandService.markAllRead(principal.memberId());
    }
}
