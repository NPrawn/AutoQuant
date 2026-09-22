import { useState } from 'react'
import { useNavigate } from 'react-router'

import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { notificationLook, notificationTime } from '../notification/labels'
import {
  useDeleteAllNotifications,
  useDeleteNotification,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
} from '../notification/queries'
import { targetRoute } from '../notification/targetRoute'
import type { AppNotification } from '../notification/types'

/*
 * 알림(목업 37). 알림은 지우기 전까지 계속 쌓여 한 장씩 넘겨 본다.
 * 지우는 것은 되돌릴 수 없지만 알림을 가리키는 기록이 없어 그대로 지운다.
 */
export function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [confirmingClear, setConfirmingClear] = useState(false)
  const notifications = useNotifications(page)
  const markAllRead = useMarkAllNotificationsRead()
  const deleteAll = useDeleteAllNotifications()

  const removeOne = useDeleteNotification()
  const [removingId, setRemovingId] = useState<number | null>(null)

  const totalPages = notifications.data?.totalPages ?? 0
  const items = notifications.data?.items ?? []
  const unreadCount = items.filter((notification) => notification.unread).length

  /* 이 장의 마지막 알림을 지우면 그 장이 사라진다. 빈 장에 남지 않게 지운 자리에서 내린다. */
  const removeNotification = (notificationId: number) => {
    setRemovingId(notificationId)
    removeOne.mutate(notificationId, {
      onSettled: () => setRemovingId(null),
      onSuccess: () => {
        if (items.length === 1 && page > 0) {
          setPage(page - 1)
        }
      },
    })
  }

  const clearAll = () => {
    deleteAll.mutate(undefined, {
      onSuccess: () => {
        setConfirmingClear(false)
        setPage(0)
      },
    })
  }

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
          <button
            className="btn ghost"
            type="button"
            disabled={items.length === 0 || deleteAll.isPending}
            onClick={() => setConfirmingClear(true)}
          >
            전체 삭제
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
      {notifications.isSuccess && items.length === 0 && (
        <EmptyState
          title="아직 알림이 없습니다"
          description="백테스트를 실행하거나 모의투자를 시작하면 끝났을 때 여기로 알려 드립니다."
        />
      )}
      {notifications.isSuccess && items.length > 0 && (
        <div className="card pad">
          {items.map((notification) => (
            <NotificationRow
              key={notification.id}
              notification={notification}
              deleting={removingId === notification.id}
              onDelete={removeNotification}
            />
          ))}
        </div>
      )}

      <Pager
        page={page}
        totalPages={totalPages}
        disabled={notifications.isFetching}
        label="알림 페이지 이동"
        onPageChange={setPage}
      />

      <ConfirmDialog
        open={confirmingClear}
        title="알림을 전부 지울까요?"
        description={`알림 ${notifications.data?.totalElements ?? 0}건이 모두 사라집니다. 되돌릴 수 없습니다.`}
        detail={deleteAll.isError ? '알림을 지우지 못했습니다. 잠시 후 다시 시도해 주세요.' : undefined}
        confirmLabel="전부 삭제"
        danger
        pending={deleteAll.isPending}
        onConfirm={clearAll}
        onCancel={() => setConfirmingClear(false)}
      />
    </section>
  )
}

type NotificationRowProps = {
  notification: AppNotification
  deleting: boolean
  onDelete: (notificationId: number) => void
}

function NotificationRow({ notification, deleting, onDelete }: NotificationRowProps) {
  const navigate = useNavigate()
  const markRead = useMarkNotificationRead()
  const look = notificationLook(notification.type)
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
  const busy = markRead.isPending || deleting

  return (
    <div className={notification.unread ? `notif unread ${look.tone}` : `notif ${look.tone}`}>
      <span className="notif-ic" aria-hidden="true">
        {look.icon}
      </span>
      <button className="notif-main" type="button" onClick={open} disabled={!canOpen || busy}>
        <b>{notification.title}</b>
        {notification.body !== null && <span className="notif-body">{notification.body}</span>}
      </button>
      <div className="notif-side">
        <time dateTime={notification.createdAt.toISOString()}>
          {notificationTime(notification.createdAt, new Date())}
        </time>
        {target !== null ? (
          <button className="rank-action" type="button" onClick={open} disabled={busy}>
            {look.action}
          </button>
        ) : (
          notification.unread && (
            <button className="rank-action" type="button" onClick={open} disabled={busy}>
              읽음
            </button>
          )
        )}
        <button
          className="rank-action"
          type="button"
          aria-label={`${notification.title} 알림 삭제`}
          disabled={busy}
          onClick={() => onDelete(notification.id)}
        >
          삭제
        </button>
      </div>
    </div>
  )
}
