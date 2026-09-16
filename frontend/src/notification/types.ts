/* backend/api 의 notification 응답과 같은 모양. 시각은 UTC ISO 문자열로 온다. */
export type NotificationType = 'BACKTEST_COMPLETED' | 'BACKTEST_FAILED' | 'SIMULATION_FAILED'

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
