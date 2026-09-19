import { Outlet } from 'react-router'

import { useCurrentMember, useLogout } from '../../auth/queries'
import { useRunningSimulationCount } from '../../simulation/queries'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

/*
 * 로그인 이후 모든 화면이 공유하는 셸. RequireAuth 가 앞에서 로그인 여부를 확인했으므로
 * 여기서 회원은 항상 있다. 960px 이하에서 사이드바를 접는 규칙은 base.css 가 담당한다.
 */
export function AppShell() {
  const { data } = useCurrentMember()
  const logout = useLogout()
  /* 아직 못 읽었으면 null 로 넘겨 모달이 실행 여부를 단정하지 않게 한다. */
  const runningSimulations = useRunningSimulationCount()

  if (!data?.member) {
    return null
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="col">
        <Topbar
          member={data.member}
          runningSimulations={runningSimulations.data ?? null}
          logoutPending={logout.isPending}
          logoutFailed={logout.isError}
          onLogout={() => logout.mutate()}
        />
        <main className="wrap">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
