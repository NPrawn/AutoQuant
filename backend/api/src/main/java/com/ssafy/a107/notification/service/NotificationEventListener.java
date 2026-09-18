package com.ssafy.a107.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationCommandService notificationCommandService;

    public NotificationEventListener(NotificationCommandService notificationCommandService) {
        this.notificationCommandService = notificationCommandService;
    }

    /*
     * 알림은 곁가지라 실패해도 원래 일을 되돌리지 않는다. 커밋 뒤에 도는 자리라 여기서 예외를
     * 올려도 백테스트 결과는 이미 저장돼 있지만, 잡지 않으면 호출한 쪽 로그가 실패처럼 보인다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationRequestedEvent event) {
        try {
            notificationCommandService.publish(
                    event.memberId(),
                    event.notificationType(),
                    event.title(),
                    event.body(),
                    event.targetId()
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to publish notification. type={} memberId={} targetId={}",
                    event.notificationType(), event.memberId(), event.targetId(), exception);
        }
    }
}
