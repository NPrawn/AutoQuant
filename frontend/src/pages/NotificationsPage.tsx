import { useNavigate } from 'react-router'

import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { notificationLooks, notificationTime } from '../notification/labels'
import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications } from '../notification/queries'
import { targetRoute } from '../notification/targetRoute'
import type { AppNotification } from '../notification/types'

/*
 * 알림(목업 37). 목업은 대회 일정과 채점 결과를 함께 그렸지만 대회 채점이 아직 없어
 * 지금은 백테스트와 모의투자에서 생긴 알림만 모인다. 대회 알림은 채점 API 가 생기면 붙는다.
 */
export function NotificationsPage() {
  const notifications = useNotifications()
  const markAllRead = useMarkAllNotificationsRead()

  const unreadCount = notifications.data?.filter((notification) => notification.unread).length ?? 0

  return (
    <section className="view active" id="view-notifications">
      <div className="page-head">
        <div>
          <span className="eyebrow">알림</span>
          <h1>알림</h1>
          <p className="sub">백테스트가 끝나거나 모의투자가 멈추면 여기에 쌓입니다.</p>
        </div>
        <div className="detail-actions">
          <button
            className="btn ghost"
            type="button"
            disabled={unreadCount === 0 || markAllRead.isPending}
            aria-busy={markAllRead.isPending}
            onClick={() => markAllRead.mutate()}
          >
            {markAllRead.isPending && <span className="spin" aria-hidden="true" />}
            모두 읽음
          </button>
        </div>
      </div>

      {markAllRead.isError && (
        <div className="callout crit" role="alert" style={{ marginBottom: 16 }}>
          <span>알림을 읽음으로 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.</span>
        </div>
      )}

      {notifications.isPending && <LoadingState label="알림을 불러오는 중…" />}
      {notifications.isError && (
        <ErrorState
          title="알림을 불러오지 못했습니다"
          error={notifications.error}
          onRetry={() => notifications.refetch()}
        />
      )}
      {notifications.isSuccess && notifications.data.length === 0 && (
        <EmptyState
          title="아직 알림이 없습니다"
          description="백테스트를 실행하거나 모의투자를 시작하면 끝났을 때 여기로 알려 드립니다."
        />
      )}
      {notifications.isSuccess && notifications.data.length > 0 && (
        <div className="card pad">
          {notifications.data.map((notification) => (
            <NotificationRow key={notification.id} notification={notification} />
          ))}
        </div>
      )}
    </section>
  )
}

type NotificationRowProps = {
  notification: AppNotification
}

function NotificationRow({ notification }: NotificationRowProps) {
  const navigate = useNavigate()
  const markRead = useMarkNotificationRead()
  const look = notificationLooks[notification.type]
  const target = targetRoute(notification)

  /* 대상으로 옮기는 김에 읽음으로 바꾼다. 실패해도 이동은 막지 않는다. */
  const open = () => {
    if (notification.unread) {
      markRead.mutate(notification.id)
    }
    if (target !== null) {
      navigate(target)
    }
  }

  /* 이동 대상이 없는 알림은 읽음 처리만 할 수 있다. 읽은 뒤에는 누를 일이 없어 잠근다. */
  const canOpen = target !== null || notification.unread

  return (
    <div className={notification.unread ? `notif unread ${look.tone}` : `notif ${look.tone}`}>
      <span className="notif-ic" aria-hidden="true">
        {look.icon}
      </span>
      <button className="notif-main" type="button" onClick={open} disabled={!canOpen || markRead.isPending}>
        <b>{notification.title}</b>
        {notification.body !== null && <p>{notification.body}</p>}
      </button>
      <div className="notif-side">
        <time dateTime={notification.createdAt.toISOString()}>
          {notificationTime(notification.createdAt, new Date())}
        </time>
        {target !== null ? (
          <button className="rank-action" type="button" onClick={open} disabled={markRead.isPending}>
            {look.action}
          </button>
        ) : (
          notification.unread && (
            <button className="rank-action" type="button" onClick={open} disabled={markRead.isPending}>
              읽음
            </button>
          )
        )}
      </div>
    </div>
  )
}
