import { request } from '../api/client'
import type {
  NotificationResponse,
  NotificationSettingsResponse,
  UnreadNotificationCountResponse,
  UpdateNotificationSettingsRequest,
} from './types'

export function fetchNotificationSettings(): Promise<NotificationSettingsResponse> {
  return request<NotificationSettingsResponse>('/notifications/settings')
}

/* 바꿀 종류만 보내고 서버는 전체를 돌려준다. */
export function updateNotificationSettings(body: UpdateNotificationSettingsRequest): Promise<NotificationSettingsResponse> {
  return request<NotificationSettingsResponse>('/notifications/settings', { method: 'PUT', body })
}

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
