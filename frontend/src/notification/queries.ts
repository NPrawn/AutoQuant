import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from './api'
import { toAppNotification } from './types'

export const notificationsQueryKey = ['notifications'] as const
export const unreadNotificationCountQueryKey = [...notificationsQueryKey, 'unread-count'] as const

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
