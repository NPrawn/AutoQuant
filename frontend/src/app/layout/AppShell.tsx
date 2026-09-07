import { Outlet } from 'react-router'

import { useCurrentMember, useLogout } from '../../auth/queries'
import { demoSession } from '../../mocks/member'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

/*
 * 로그인 이후 모든 화면이 공유하는 셸. RequireAuth 가 앞에서 로그인 여부를 확인했으므로
 * 여기서 회원은 항상 있다. 960px 이하에서 사이드바를 접는 규칙은 base.css 가 담당한다.
 */
export function AppShell() {
  const { data: member } = useCurrentMember()
  const logout = useLogout()

  if (!member) {
    return null
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="col">
        <Topbar member={member} session={demoSession} onLogout={() => logout.mutate()} />
        <main className="wrap">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
