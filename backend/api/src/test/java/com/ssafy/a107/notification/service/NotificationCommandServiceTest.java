package com.ssafy.a107.notification.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import com.ssafy.a107.notification.domain.Notification;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.repository.NotificationPreferenceRepository;
import com.ssafy.a107.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class NotificationCommandServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_MEMBER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationPreferenceRepository notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final NotificationCommandService service = new NotificationCommandService(
            notificationRepository, notificationPreferenceRepository, memberRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @DisplayName("알림을 회원 앞으로 남긴다")
    @Test
    void publishNotification() {
        Member member = mock(Member.class);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Notification notification = service.publish(
                MEMBER_ID, NotificationType.BACKTEST_COMPLETED, "백테스트가 끝났습니다", "결과를 확인하세요", "run-1")
                .orElseThrow();

        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.BACKTEST_COMPLETED);
        assertThat(notification.getTitle()).isEqualTo("백테스트가 끝났습니다");
        assertThat(notification.getTargetId()).isEqualTo("run-1");
        assertThat(notification.isUnread()).isTrue();
    }

    @DisplayName("회원이 꺼 둔 종류의 알림은 만들지 않는다")
    @Test
    void skipPublishWhenTypeDisabled() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(mock(Member.class)));
        given(notificationPreferenceRepository.existsByMemberMemberIdAndNotificationTypeAndEnabledFalse(
                MEMBER_ID, NotificationType.BACKTEST_COMPLETED)).willReturn(true);

        Optional<Notification> notification = service.publish(
                MEMBER_ID, NotificationType.BACKTEST_COMPLETED, "백테스트가 끝났습니다", null, "run-1");

        assertThat(notification).isEmpty();
        then(notificationRepository).should(never()).save(any());
    }

    @DisplayName("없는 회원 앞으로는 알림을 남기지 않는다")
    @Test
    void rejectPublishForMissingMember() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(
                MEMBER_ID, NotificationType.BACKTEST_COMPLETED, "제목", null, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.MEMBER_NOT_FOUND);

        then(notificationRepository).should(never()).save(any());
    }

    @DisplayName("읽음 처리는 읽은 시각을 남긴다")
    @Test
    void markRead() {
        Notification notification = notification();
        given(notificationRepository.findByNotificationIdAndMemberMemberId(7L, MEMBER_ID))
                .willReturn(Optional.of(notification));

        service.markRead(MEMBER_ID, 7L);

        assertThat(notification.getReadAt()).isEqualTo(NOW);
        assertThat(notification.isUnread()).isFalse();
    }

    @DisplayName("이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다")
    @Test
    void keepFirstReadAt() {
        Notification notification = notification();
        Instant firstReadAt = Instant.parse("2026-09-15T00:00:00Z");
        notification.read(firstReadAt);
        given(notificationRepository.findByNotificationIdAndMemberMemberId(7L, MEMBER_ID))
                .willReturn(Optional.of(notification));

        service.markRead(MEMBER_ID, 7L);

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @DisplayName("남의 알림은 읽음 처리할 수 없다")
    @Test
    void rejectMarkReadOfOtherMember() {
        given(notificationRepository.findByNotificationIdAndMemberMemberId(7L, OTHER_MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(OTHER_MEMBER_ID, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @DisplayName("모두 읽음은 안 읽은 알림만 읽음으로 바꾼다")
    @Test
    void markAllRead() {
        Notification first = notification();
        Notification second = notification();
        given(notificationRepository.findAllByMemberMemberIdAndReadAtIsNull(MEMBER_ID))
                .willReturn(List.of(first, second));

        service.markAllRead(MEMBER_ID);

        assertThat(first.getReadAt()).isEqualTo(NOW);
        assertThat(second.getReadAt()).isEqualTo(NOW);
    }

    private Notification notification() {
        Member member = mock(Member.class);
        return new Notification(member, NotificationType.BACKTEST_COMPLETED, "백테스트가 끝났습니다", "본문", "run-1");
    }
}
