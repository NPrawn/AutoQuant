import { request } from '../api/client'
import type { NotificationResponse, UnreadNotificationCountResponse } from './types'

export function fetchNotifications(): Promise<NotificationResponse[]> {
  return request<NotificationResponse[]>('/notifications')
}

export function fetchUnreadNotificationCount(): Promise<UnreadNotificationCountResponse> {
  return request<UnreadNotificationCountResponse>('/notifications/unread-count')
}

export function markNotificationRead(notificationId: number): Promise<void> {
  return request<void>(`/notifications/${notificationId}/read`, { method: 'POST' })
}

export function markAllNotificationsRead(): Promise<void> {
  return request<void>('/notifications/read-all', { method: 'POST' })
}
