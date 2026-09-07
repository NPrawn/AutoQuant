import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router'

import { routes } from '../app/routes'
import { useCurrentMember } from './queries'

/*
 * 인증이 필요한 화면을 감싼다. 로그인 여부를 확인하는 동안과 서버에 닿지 못할 때는
 * 로그인 화면으로 튕기지 않고 그 상태를 보여 준다. 튕기면 사용자는 로그아웃된 줄 안다.
 */
export function RequireAuth() {
  const { data: member, isPending, isError, refetch } = useCurrentMember()

  if (isPending) {
    return <AuthGate>로그인 상태를 확인하고 있습니다…</AuthGate>
  }
  if (isError) {
    return (
      <AuthGate>
        서버에 연결할 수 없습니다.{' '}
        <button className="btn" type="button" onClick={() => refetch()}>
          다시 시도
        </button>
      </AuthGate>
    )
  }
  if (!member) {
    return <Navigate to={routes.login} replace />
  }
  return <Outlet />
}

function AuthGate({ children }: { children: ReactNode }) {
  return (
    <div className="login" style={{ minHeight: '100vh' }}>
      <p className="eyebrow" role="status" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {children}
      </p>
    </div>
  )
}
