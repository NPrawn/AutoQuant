package com.ssafy.a107.notification.repository;

import com.ssafy.a107.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByMemberMemberIdOrderByNotificationIdDesc(UUID memberId, Pageable pageable);

    Optional<Notification> findByNotificationIdAndMemberMemberId(Long notificationId, UUID memberId);

    long countByMemberMemberIdAndReadAtIsNull(UUID memberId);

    /*
     * 모두 읽음은 읽지 않은 것만 불러 도메인 메서드로 바꾼다. 한 회원의 안 읽은 알림은
     * 많아야 수십 건이라 벌크 UPDATE 로 얻을 이득보다 updated_at 을 엔티티와 어긋나게 둘 위험이 크다.
     */
    List<Notification> findAllByMemberMemberIdAndReadAtIsNull(UUID memberId);

    /* 지운 건수로 남의 알림이나 없는 알림을 걸러낸다. */
    @Modifying
    @Query("delete from Notification n where n.notificationId = :notificationId and n.member.memberId = :memberId")
    int deleteOwned(@Param("notificationId") Long notificationId, @Param("memberId") UUID memberId);

    /* 쌓인 알림을 한 번에 지운다. 읽음 처리와 달리 엔티티로 바꿀 상태가 없어 벌크로 지운다. */
    @Modifying
    @Query("delete from Notification n where n.member.memberId = :memberId")
    int deleteAllOwned(@Param("memberId") UUID memberId);
}
