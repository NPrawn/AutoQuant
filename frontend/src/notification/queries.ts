import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  fetchNotificationSettings,
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationSettings,
} from './api'
import { toAppNotification, toNotificationSettings, type NotificationSettingsResponse } from './types'

export const notificationsQueryKey = ['notifications'] as const
export const unreadNotificationCountQueryKey = [...notificationsQueryKey, 'unread-count'] as const
export const notificationSettingsQueryKey = ['notification-settings'] as const

export function useNotificationSettings() {
  return useQuery({
    queryKey: notificationSettingsQueryKey,
    queryFn: fetchNotificationSettings,
    select: toNotificationSettings,
  })
}

/*
 * 토글은 누르는 즉시 바뀐 것처럼 보이고 서버에 저장한다. 실패하면 이전 값으로 되돌리고
 * 화면이 사유를 보인다. 끝나면 서버 값으로 한 번 맞춘다.
 */
export function useUpdateNotificationSetting() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ type, enabled }: { type: string; enabled: boolean }) =>
      updateNotificationSettings({ settings: [{ notificationType: type, enabled }] }),
    onMutate: async ({ type, enabled }) => {
      await queryClient.cancelQueries({ queryKey: notificationSettingsQueryKey })
      const previous = queryClient.getQueryData<NotificationSettingsResponse>(notificationSettingsQueryKey)
      if (previous !== undefined) {
        queryClient.setQueryData<NotificationSettingsResponse>(notificationSettingsQueryKey, {
          settings: previous.settings.map((setting) => (setting.notificationType === type ? { ...setting, enabled } : setting)),
        })
      }
      return { previous }
    },
    onError: (_error, _variables, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(notificationSettingsQueryKey, context.previous)
      }
    },
    onSuccess: (response) => queryClient.setQueryData(notificationSettingsQueryKey, response),
    onSettled: () => queryClient.invalidateQueries({ queryKey: notificationSettingsQueryKey }),
  })
}

/*
 * 알림은 사용자가 보지 않는 동안 쌓인다. 화면에 있는 동안 주기적으로 다시 물어
 * 백테스트가 끝난 것을 목록을 새로 고치지 않고도 알 수 있게 한다.
 */
const pollIntervalMs = 30 * 1000

export function useNotifications() {
  return useQuery({
    queryKey: notificationsQueryKey,
    queryFn: fetchNotifications,
    select: (responses) => responses.map(toAppNotification),
    refetchInterval: pollIntervalMs,
  })
}

/* 상단 배지. 목록보다 가벼워 다른 화면에서도 계속 돈다. */
export function useUnreadNotificationCount() {
  return useQuery({
    queryKey: unreadNotificationCountQueryKey,
    queryFn: fetchUnreadNotificationCount,
    select: (response) => response.unreadCount,
    refetchInterval: pollIntervalMs,
    refetchIntervalInBackground: true,
  })
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationsQueryKey }),
  })
}

export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationsQueryKey }),
  })
}
