package com.ssafy.a107.notification.repository;

import com.ssafy.a107.notification.domain.NotificationPreference;
import com.ssafy.a107.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findAllByMemberMemberId(UUID memberId);

    /* 알림을 만들기 직전에 한 번 묻는다. 행이 없으면 켜진 것이라 꺼진 행이 있는지만 본다. */
    boolean existsByMemberMemberIdAndNotificationTypeAndEnabledFalse(UUID memberId, NotificationType notificationType);
}
