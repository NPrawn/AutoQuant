import { Link, useMatches } from 'react-router'

import { ThemeToggle } from '../../styles/ThemeToggle'
import { routes } from '../routes'

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
  memberName: string
}

export function Topbar({ memberName }: TopbarProps) {
  const title = useCurrentTitle()

  return (
    <header className="topbar">
      <div className="crumb">
        <b>{title}</b>
      </div>
      <div className="topbar-r">
        <ThemeToggle />
        <Link to={routes.notifications} className="icon-btn" title="알림" aria-label="알림">
          <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M18 15v-4a6 6 0 1 0-12 0v4l-1.5 3h15z" />
            <path d="M10 21h4" />
          </svg>
        </Link>
        <span className="tag">데모 데이터</span>
        <div className="acct">
          {/* 드롭다운 메뉴는 계정 메뉴 작업에서 붙인다. */}
          <button className="avatar" type="button" aria-label="계정 메뉴">
            {memberName}
          </button>
        </div>
      </div>
    </header>
  )
}
