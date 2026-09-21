/* backend/api 의 notification 응답과 같은 모양. 시각은 UTC ISO 문자열로 온다. */
export type NotificationType = 'BACKTEST_COMPLETED' | 'BACKTEST_FAILED' | 'SIMULATION_FAILED' | 'CONTEST_SCORED'

export type NotificationResponse = {
  notificationId: number
  notificationType: NotificationType
  title: string
  body: string | null
  targetId: string | null
  readAt: string | null
  createdAt: string
}

export type UnreadNotificationCountResponse = {
  unreadCount: number
}

/* --- /notifications/settings --- */

export type NotificationSettingResponse = {
  notificationType: string
  enabled: boolean
}

/* 서버는 종류 네 가지를 모두 채워 준다. 저장한 적 없는 종류는 켜진 것으로 온다. */
export type NotificationSettingsResponse = {
  settings: NotificationSettingResponse[]
}

export type UpdateNotificationSettingsRequest = {
  settings: { notificationType: string; enabled: boolean }[]
}

/* 화면이 쓰는 설정 한 줄. 종류 문자열은 서버 값을 그대로 둬 모르는 종류도 토글로 보인다. */
export type NotificationSetting = {
  type: string
  enabled: boolean
}

export function toNotificationSettings(response: NotificationSettingsResponse): NotificationSetting[] {
  return response.settings.map((setting) => ({ type: setting.notificationType, enabled: setting.enabled }))
}

/* 화면이 쓰는 모델. 읽음 여부는 읽은 시각에서 끌어내 화면이 다시 판단하지 않는다. */
export type AppNotification = {
  id: number
  type: NotificationType
  title: string
  body: string | null
  targetId: string | null
  unread: boolean
  createdAt: Date
}

export function toAppNotification(response: NotificationResponse): AppNotification {
  return {
    id: response.notificationId,
    type: response.notificationType,
    title: response.title,
    body: response.body,
    targetId: response.targetId,
    unread: response.readAt === null,
    createdAt: new Date(response.createdAt),
  }
}
