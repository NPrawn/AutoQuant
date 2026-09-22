package com.ssafy.a107.notification.service;

import com.ssafy.a107.notification.domain.Notification;
import com.ssafy.a107.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationReadService {

    /* 한 장 크기의 상한. 화면이 더 큰 값을 보내도 여기서 자른다. */
    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public NotificationReadService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Page<Notification> getNotifications(UUID memberId, int page, int size) {
        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);

        return notificationRepository.findAllByMemberMemberIdOrderByNotificationIdDesc(
                memberId, PageRequest.of(pageNumber, pageSize));
    }

    public long countUnread(UUID memberId) {
        return notificationRepository.countByMemberMemberIdAndReadAtIsNull(memberId);
    }
}
