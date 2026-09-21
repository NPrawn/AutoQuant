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

import java.util.Objects;

/*
 * 회원이 알림 종류마다 켜고 끈 값. 행이 없으면 켜진 것이다.
 * 알림을 만드는 쪽이 저장 전에 이 값을 보고 꺼진 종류는 만들지 않는다.
 */
@Entity
@Table(name = "notification_preference", schema = "notification")
public class NotificationPreference extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_preference_id", nullable = false, updatable = false)
    private Long notificationPreferenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_notification_preference_member")
    )
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40, updatable = false)
    private NotificationType notificationType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected NotificationPreference() {
    }

    public NotificationPreference(Member member, NotificationType notificationType, boolean enabled) {
        this.member = Objects.requireNonNull(member);
        this.notificationType = Objects.requireNonNull(notificationType);
        this.enabled = enabled;
    }

    public void change(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getNotificationPreferenceId() {
        return notificationPreferenceId;
    }

    public Member getMember() {
        return member;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
