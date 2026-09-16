package com.ssafy.a107.notification.repository;

import com.ssafy.a107.notification.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByMemberMemberIdOrderByNotificationIdDesc(UUID memberId, Pageable pageable);

    Optional<Notification> findByNotificationIdAndMemberMemberId(Long notificationId, UUID memberId);

    long countByMemberMemberIdAndReadAtIsNull(UUID memberId);

    /*
     * 모두 읽음은 읽지 않은 것만 불러 도메인 메서드로 바꾼다. 한 회원의 안 읽은 알림은
     * 많아야 수십 건이라 벌크 UPDATE 로 얻을 이득보다 updated_at 을 엔티티와 어긋나게 둘 위험이 크다.
     */
    List<Notification> findAllByMemberMemberIdAndReadAtIsNull(UUID memberId);
}
