import { useState } from 'react'
import { useNavigate } from 'react-router'

import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { notificationLook, notificationTime } from '../notification/labels'
import {
  useDeleteAllNotifications,
  useDeleteNotifications,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
} from '../notification/queries'
import { targetRoute } from '../notification/targetRoute'
import type { AppNotification } from '../notification/types'

/*
 * 알림(목업 37). 알림은 지우기 전까지 계속 쌓여 한 장씩 넘겨 본다.
 * 삭제는 고르는 모드로 들어가서 한다. 행마다 버튼을 늘 띄우면 누를 일 없는 버튼이 목록을 채운다.
 */
export function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [selecting, setSelecting] = useState(false)
  const [selected, setSelected] = useState<number[]>([])
  const [confirming, setConfirming] = useState<'selected' | 'all' | null>(null)

  const notifications = useNotifications(page)
  const markAllRead = useMarkAllNotificationsRead()
  const deleteSelected = useDeleteNotifications()
  const deleteAll = useDeleteAllNotifications()

  const totalPages = notifications.data?.totalPages ?? 0
  const totalElements = notifications.data?.totalElements ?? 0
  const items = notifications.data?.items ?? []
  const unreadCount = items.filter((notification) => notification.unread).length
  const allSelected = items.length > 0 && items.every((notification) => selected.includes(notification.id))
  const pending = deleteSelected.isPending || deleteAll.isPending

  const leaveSelecting = () => {
    setSelecting(false)
    setSelected([])
  }

  const toggle = (notificationId: number) => {
    setSelected((current) =>
      current.includes(notificationId)
        ? current.filter((id) => id !== notificationId)
        : [...current, notificationId],
    )
  }

  const toggleAll = () => {
    setSelected(allSelected ? [] : items.map((notification) => notification.id))
  }

  /* 이 장을 통째로 지우면 그 장이 사라진다. 빈 장에 남지 않게 지운 자리에서 내린다. */
  const afterDelete = (removed: number) => {
    setConfirming(null)
    leaveSelecting()
    if (removed >= items.length && page > 0) {
      setPage(page - 1)
    }
  }

  const removeSelected = () => {
    deleteSelected.mutate(selected, { onSuccess: () => afterDelete(selected.length) })
  }

  const removeAll = () => {
    deleteAll.mutate(undefined, {
      onSuccess: () => {
        setConfirming(null)
        leaveSelecting()
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
          {selecting ? (
            <>
              <button className="btn ghost" type="button" disabled={items.length === 0 || pending} onClick={toggleAll}>
                {allSelected ? '선택 해제' : '이 장 전체'}
              </button>
              <button
                className="btn ghost"
                type="button"
                disabled={selected.length === 0 || pending}
                onClick={() => setConfirming('selected')}
              >
                {selected.length === 0 ? '삭제' : `삭제 ${selected.length}건`}
              </button>
              <button className="btn ghost" type="button" disabled={pending} onClick={leaveSelecting}>
                취소
              </button>
            </>
          ) : (
            <>
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
                disabled={totalElements === 0}
                onClick={() => setSelecting(true)}
              >
                삭제
              </button>
            </>
          )}
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
              selecting={selecting}
              checked={selected.includes(notification.id)}
              disabled={pending}
              onToggle={toggle}
            />
          ))}
        </div>
      )}

      {selecting && items.length > 0 && (
        <p className="field-note" style={{ marginTop: 12 }}>
          고른 알림만 지웁니다. 전체 {totalElements}건을 한 번에 지우려면{' '}
          <button className="rank-action" type="button" disabled={pending} onClick={() => setConfirming('all')}>
            전체 삭제
          </button>
          를 누르세요.
        </p>
      )}

      <Pager
        page={page}
        totalPages={totalPages}
        disabled={notifications.isFetching || pending}
        label="알림 페이지 이동"
        onPageChange={setPage}
      />

      <ConfirmDialog
        open={confirming === 'selected'}
        title="고른 알림을 지울까요?"
        description={`알림 ${selected.length}건이 사라집니다. 되돌릴 수 없습니다.`}
        detail={deleteSelected.isError ? '알림을 지우지 못했습니다. 잠시 후 다시 시도해 주세요.' : undefined}
        confirmLabel="삭제"
        danger
        pending={deleteSelected.isPending}
        onConfirm={removeSelected}
        onCancel={() => setConfirming(null)}
      />

      <ConfirmDialog
        open={confirming === 'all'}
        title="알림을 전부 지울까요?"
        description={`알림 ${totalElements}건이 모두 사라집니다. 되돌릴 수 없습니다.`}
        detail={deleteAll.isError ? '알림을 지우지 못했습니다. 잠시 후 다시 시도해 주세요.' : undefined}
        confirmLabel="전부 삭제"
        danger
        pending={deleteAll.isPending}
        onConfirm={removeAll}
        onCancel={() => setConfirming(null)}
      />
    </section>
  )
}

type NotificationRowProps = {
  notification: AppNotification
  selecting: boolean
  checked: boolean
  disabled: boolean
  onToggle: (notificationId: number) => void
}

function NotificationRow({ notification, selecting, checked, disabled, onToggle }: NotificationRowProps) {
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

  /* 고르는 중에는 본문을 눌러도 이동하지 않고 선택만 바뀐다. */
  const press = () => {
    if (selecting) {
      onToggle(notification.id)
      return
    }
    open()
  }

  /* 이동 대상이 없는 알림은 읽음 처리만 할 수 있다. 읽은 뒤에는 누를 일이 없어 잠근다. */
  const canPress = selecting || target !== null || notification.unread

  return (
    <div className={notification.unread ? `notif unread ${look.tone}` : `notif ${look.tone}`}>
      {selecting ? (
        <input
          type="checkbox"
          className="notif-ic"
          checked={checked}
          disabled={disabled}
          aria-label={`${notification.title} 선택`}
          onChange={() => onToggle(notification.id)}
        />
      ) : (
        <span className="notif-ic" aria-hidden="true">
          {look.icon}
        </span>
      )}
      <button className="notif-main" type="button" onClick={press} disabled={!canPress || markRead.isPending || disabled}>
        <b>{notification.title}</b>
        {notification.body !== null && <span className="notif-body">{notification.body}</span>}
      </button>
      <div className="notif-side">
        <time dateTime={notification.createdAt.toISOString()}>
          {notificationTime(notification.createdAt, new Date())}
        </time>
        {!selecting && target !== null && (
          <button className="rank-action" type="button" onClick={open} disabled={markRead.isPending}>
            {look.action}
          </button>
        )}
        {!selecting && target === null && notification.unread && (
          <button className="rank-action" type="button" onClick={open} disabled={markRead.isPending}>
            읽음
          </button>
        )}
      </div>
    </div>
  )
}
