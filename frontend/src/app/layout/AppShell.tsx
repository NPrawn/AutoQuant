import { Outlet } from 'react-router'

import { demoMember, demoSession } from '../../mocks/member'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

/*
 * 로그인 이후 모든 화면이 공유하는 셸. 960px 이하에서 사이드바를 접는 규칙은
 * base.css 의 .shell / .rail 미디어 쿼리가 담당한다.
 */
export function AppShell() {
  return (
    <div className="shell">
      <Sidebar />
      <div className="col">
        <Topbar member={demoMember} session={demoSession} />
        <main className="wrap">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
