package com.ssafy.a107.notification.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.notification.controller.request.UpdateNotificationSettingsRequest;
import com.ssafy.a107.notification.controller.response.NotificationPageResponse;
import com.ssafy.a107.notification.controller.response.NotificationSettingsResponse;
import com.ssafy.a107.notification.controller.response.UnreadNotificationCountResponse;
import com.ssafy.a107.notification.service.NotificationCommandService;
import com.ssafy.a107.notification.service.NotificationPreferenceService;
import com.ssafy.a107.notification.service.NotificationReadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationReadService notificationReadService;
    private final NotificationCommandService notificationCommandService;
    private final NotificationPreferenceService notificationPreferenceService;

    public NotificationController(
            NotificationReadService notificationReadService,
            NotificationCommandService notificationCommandService,
            NotificationPreferenceService notificationPreferenceService
    ) {
        this.notificationReadService = notificationReadService;
        this.notificationCommandService = notificationCommandService;
        this.notificationPreferenceService = notificationPreferenceService;
    }

    /* 알림 설정. 종류 네 가지가 모두 오고 저장한 적 없는 종류는 켜진 것이다. */
    @GetMapping("/settings")
    public NotificationSettingsResponse getSettings(@AuthenticationPrincipal MemberPrincipal principal) {
        return NotificationSettingsResponse.from(notificationPreferenceService.getPreferences(principal.memberId()));
    }

    @PutMapping("/settings")
    public NotificationSettingsResponse updateSettings(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        return NotificationSettingsResponse.from(
                notificationPreferenceService.updatePreferences(principal.memberId(), request.toChanges()));
    }

    @GetMapping
    public NotificationPageResponse getNotifications(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return NotificationPageResponse.from(notificationReadService.getNotifications(principal.memberId(), page, size));
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

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long notificationId
    ) {
        notificationCommandService.delete(principal.memberId(), notificationId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationCommandService.deleteAll(principal.memberId());
    }
}
