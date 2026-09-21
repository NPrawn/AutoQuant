package com.ssafy.a107.notification.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import com.ssafy.a107.notification.domain.NotificationPreference;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class NotificationPreferenceServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final NotificationPreferenceService service = new NotificationPreferenceService(repository, memberRepository);

    @DisplayName("저장된 행이 없으면 네 종류가 모두 켜진 것으로 답한다")
    @Test
    void defaultEverythingEnabled() {
        given(repository.findAllByMemberMemberId(MEMBER_ID)).willReturn(List.of());

        Map<NotificationType, Boolean> preferences = service.getPreferences(MEMBER_ID);

        assertThat(preferences).hasSize(NotificationType.values().length);
        assertThat(preferences.values()).containsOnly(true);
    }

    @DisplayName("저장된 행은 그 값으로, 없는 종류는 켜진 것으로 답한다")
    @Test
    void mergeStoredWithDefaults() {
        Member member = mock(Member.class);
        given(repository.findAllByMemberMemberId(MEMBER_ID))
                .willReturn(List.of(new NotificationPreference(member, NotificationType.BACKTEST_FAILED, false)));

        Map<NotificationType, Boolean> preferences = service.getPreferences(MEMBER_ID);

        assertThat(preferences.get(NotificationType.BACKTEST_FAILED)).isFalse();
        assertThat(preferences.get(NotificationType.BACKTEST_COMPLETED)).isTrue();
        assertThat(preferences.get(NotificationType.CONTEST_SCORED)).isTrue();
    }

    @DisplayName("요청에 든 종류만 바꾸고 없던 종류는 행을 새로 만든다")
    @Test
    void updateOnlyRequestedTypes() {
        Member member = mock(Member.class);
        NotificationPreference existing = new NotificationPreference(member, NotificationType.BACKTEST_FAILED, false);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(repository.findAllByMemberMemberId(MEMBER_ID)).willReturn(List.of(existing));
        given(repository.save(any(NotificationPreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        Map<NotificationType, Boolean> preferences = service.updatePreferences(MEMBER_ID, Map.of(
                NotificationType.BACKTEST_FAILED, true,
                NotificationType.CONTEST_SCORED, false
        ));

        assertThat(existing.isEnabled()).isTrue();
        assertThat(preferences.get(NotificationType.BACKTEST_FAILED)).isTrue();
        assertThat(preferences.get(NotificationType.CONTEST_SCORED)).isFalse();
        assertThat(preferences.get(NotificationType.SIMULATION_FAILED)).isTrue();
        then(repository).should().save(any(NotificationPreference.class));
    }

    @DisplayName("없는 회원의 설정은 바꿀 수 없다")
    @Test
    void rejectUpdateForMissingMember() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePreferences(MEMBER_ID, Map.of(NotificationType.BACKTEST_FAILED, false)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.MEMBER_NOT_FOUND);

        then(repository).should(never()).save(any());
    }
}
