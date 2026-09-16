package com.ssafy.a107.notification.service;

import com.ssafy.a107.notification.domain.Notification;
import com.ssafy.a107.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationReadService {

    /*
     * 알림 화면은 최근 것만 보여 준다. 페이지를 넘기는 화면이 아니라 상한을 두고 끊는다.
     * 넘치는 옛 알림은 목록에서 빠지지만 안 읽은 수에는 그대로 센다.
     */
    private static final int MAX_NOTIFICATIONS = 100;

    private final NotificationRepository notificationRepository;

    public NotificationReadService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getNotifications(UUID memberId) {
        return notificationRepository.findAllByMemberMemberIdOrderByNotificationIdDesc(
                memberId, PageRequest.of(0, MAX_NOTIFICATIONS));
    }

    public long countUnread(UUID memberId) {
        return notificationRepository.countByMemberMemberIdAndReadAtIsNull(memberId);
    }
}
