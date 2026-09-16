import { Link, useMatches } from 'react-router'

import { useUnreadNotificationCount } from '../../notification/queries'
import { ThemeToggle } from '../../styles/ThemeToggle'
import { routes } from '../routes'
import { AccountMenu } from './AccountMenu'

/* 라우트 정의의 handle.title 을 상단바 제목으로 쓴다. 가장 깊은 매치가 이긴다. */
type RouteHandle = { title?: string }

function useCurrentTitle(): string | undefined {
  const matches = useMatches()
  for (let index = matches.length - 1; index >= 0; index -= 1) {
    const handle = matches[index].handle as RouteHandle | undefined
    if (handle?.title) {
      return handle.title
    }
  }
  return undefined
}

type TopbarProps = {
  member: { displayName: string; emailAddress: string }
  runningSimulations: number
  onLogout?: () => void
}

export function Topbar({ member, runningSimulations, onLogout }: TopbarProps) {
  const title = useCurrentTitle()
  /* 아직 못 읽었으면 0 으로 두어 있지도 않은 알림을 배지로 알리지 않는다. */
  const unreadCount = useUnreadNotificationCount().data ?? 0

  return (
    <header className="topbar">
      <div className="crumb">
        <b>{title}</b>
      </div>
      <div className="topbar-r">
        <ThemeToggle />
        <Link
          to={routes.notifications}
          className="icon-btn"
          title="알림"
          aria-label={unreadCount > 0 ? `알림 ${unreadCount}건 안 읽음` : '알림'}
        >
          <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M18 15v-4a6 6 0 1 0-12 0v4l-1.5 3h15z" />
            <path d="M10 21h4" />
          </svg>
          {unreadCount > 0 && (
            <span className="dot-count" aria-hidden="true">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </Link>
        <AccountMenu
          displayName={member.displayName}
          emailAddress={member.emailAddress}
          runningSimulations={runningSimulations}
          onLogout={onLogout}
        />
      </div>
    </header>
  )
}
