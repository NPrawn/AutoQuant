package com.ssafy.a107.notification.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import com.ssafy.a107.notification.domain.Notification;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public NotificationCommandService(
            NotificationRepository notificationRepository,
            MemberRepository memberRepository,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /*
     * 알림을 남기는 쪽은 백테스트·모의투자 실행이다. 그 트랜잭션이 알림 때문에 되돌아가면
     * 정작 중요한 실행 결과를 잃는다. 그래서 새 트랜잭션에서 따로 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification publish(
            UUID memberId,
            NotificationType notificationType,
            String title,
            String body,
            String targetId
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.MEMBER_NOT_FOUND));

        return notificationRepository.save(new Notification(member, notificationType, title, body, targetId));
    }

    @Transactional
    public void markRead(UUID memberId, Long notificationId) {
        Notification notification = notificationRepository
                .findByNotificationIdAndMemberMemberId(notificationId, memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOTIFICATION_NOT_FOUND));

        notification.read(Instant.now(clock));
    }

    @Transactional
    public void markAllRead(UUID memberId) {
        List<Notification> unread = notificationRepository.findAllByMemberMemberIdAndReadAtIsNull(memberId);
        Instant readAt = Instant.now(clock);
        unread.forEach(notification -> notification.read(readAt));
    }
}
