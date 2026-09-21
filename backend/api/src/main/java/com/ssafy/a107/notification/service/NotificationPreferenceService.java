package com.ssafy.a107.notification.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.member.repository.MemberRepository;
import com.ssafy.a107.notification.domain.NotificationPreference;
import com.ssafy.a107.notification.domain.NotificationType;
import com.ssafy.a107.notification.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 회원의 알림 종류별 켬·끔. 저장된 행이 없는 종류는 켜진 것으로 답한다.
 * 응답은 항상 네 종류를 모두 채워 화면이 빠진 종류를 기본값으로 다시 채우지 않게 한다.
 */
@Service
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final MemberRepository memberRepository;

    public NotificationPreferenceService(
            NotificationPreferenceRepository notificationPreferenceRepository,
            MemberRepository memberRepository
    ) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Map<NotificationType, Boolean> getPreferences(UUID memberId) {
        return withDefaults(notificationPreferenceRepository.findAllByMemberMemberId(memberId));
    }

    /* 요청에 든 종류만 바꾸고 나머지는 그대로 둔다. 없던 종류는 행을 새로 만든다. */
    public Map<NotificationType, Boolean> updatePreferences(UUID memberId, Map<NotificationType, Boolean> changes) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.MEMBER_NOT_FOUND));

        List<NotificationPreference> existing = notificationPreferenceRepository.findAllByMemberMemberId(memberId);
        Map<NotificationType, NotificationPreference> byType = new EnumMap<>(NotificationType.class);
        existing.forEach(preference -> byType.put(preference.getNotificationType(), preference));

        changes.forEach((type, enabled) -> {
            NotificationPreference preference = byType.get(type);
            if (preference == null) {
                byType.put(type, notificationPreferenceRepository.save(new NotificationPreference(member, type, enabled)));
            } else {
                preference.change(enabled);
            }
        });

        return withDefaults(byType.values());
    }

    private static Map<NotificationType, Boolean> withDefaults(Iterable<NotificationPreference> preferences) {
        Map<NotificationType, Boolean> result = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            result.put(type, Boolean.TRUE);
        }
        preferences.forEach(preference -> result.put(preference.getNotificationType(), preference.isEnabled()));
        return result;
    }
}
