package com.ssafy.a107.notification.domain;

import com.ssafy.a107.common.domain.BaseEntity;
import com.ssafy.a107.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification", schema = "notification")
public class Notification extends BaseEntity {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_TARGET_ID_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id", nullable = false, updatable = false)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_notification_member")
    )
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40, updatable = false)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH, updatable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "target_id", length = MAX_TARGET_ID_LENGTH, updatable = false)
    private String targetId;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(Member member, NotificationType notificationType, String title, String body, String targetId) {
        this.member = Objects.requireNonNull(member);
        this.notificationType = Objects.requireNonNull(notificationType);
        this.title = requireTitle(title);
        this.body = body;
        this.targetId = requireTargetId(targetId);
    }

    /* 이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다. */
    public void read(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = Objects.requireNonNull(readAt);
        }
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public boolean isOwnedBy(UUID memberId) {
        return member.getMemberId().equals(memberId);
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title must not be blank");
        }

        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            return normalizedTitle.substring(0, MAX_TITLE_LENGTH);
        }
        return normalizedTitle;
    }

    private static String requireTargetId(String targetId) {
        if (targetId != null && targetId.length() > MAX_TARGET_ID_LENGTH) {
            throw new IllegalArgumentException("Notification target id must not exceed 64 characters");
        }
        return targetId;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public Member getMember() {
        return member;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getTargetId() {
        return targetId;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
