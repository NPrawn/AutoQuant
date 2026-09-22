import { request } from '../api/client'
import type {
  NotificationPageResponse,
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

export function fetchNotifications(page: number, size: number): Promise<NotificationPageResponse> {
  return request<NotificationPageResponse>(`/notifications?page=${page}&size=${size}`)
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

export function deleteNotification(notificationId: number): Promise<void> {
  return request<void>(`/notifications/${notificationId}`, { method: 'DELETE' })
}

/* 내 알림을 전부 지운다. 되돌릴 수 없어 화면이 한 번 묻는다. */
export function deleteAllNotifications(): Promise<void> {
  return request<void>('/notifications', { method: 'DELETE' })
}
