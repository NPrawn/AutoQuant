import type { ReactNode } from 'react'
import { useState } from 'react'
import { Navigate, Outlet } from 'react-router'

import { errorCodes } from '../api/errorCodes'
import { routes } from '../app/routes'
import { ErrorState } from '../components/StateBlock'
import { LoadingState } from '../components/LoadingState'
import { useCurrentMember } from './queries'

/*
 * 인증이 필요한 화면을 감싼다. 로그인 여부를 확인하는 동안과 서버에 닿지 못할 때는
 * 로그인 화면으로 튕기지 않고 그 상태를 보여 준다. 튕기면 사용자는 로그아웃된 줄 안다.
 */
export function RequireAuth() {
  const { data, isPending, isError, error, refetch } = useCurrentMember()
  const member = data?.member ?? null

  /*
   * 이 세션에서 한 번이라도 로그인돼 있었는지. 그랬다면 지금의 401 은 만료다.
   * 쿠키가 Max-Age 로 사라지면 코드가 "인증 필요"로 오기 때문에 코드만으로는
   * 처음 방문과 만료를 구분할 수 없다. 이전 렌더 값을 기억하는 React 권장 패턴이다.
   */
  const [hadMember, setHadMember] = useState(false)
  if (member && !hadMember) {
    setHadMember(true)
  }

  if (isPending) {
    return (
      <Gate>
        <LoadingState label="로그인 상태를 확인하고 있습니다…" />
      </Gate>
    )
  }
  if (isError) {
    return (
      <Gate>
        <ErrorState title="서버에 연결할 수 없습니다" error={error} onRetry={() => refetch()} />
      </Gate>
    )
  }
  if (member) {
    return <Outlet />
  }

  return <Navigate to={loginLocation(data.unauthorizedCode, hadMember)} replace />
}

/*
 * 처음부터 로그인이 없던 방문은 조용히 로그인 화면으로 보낸다. 토큰이 무효하거나
 * 로그인돼 있다가 끊긴 경우에만 이유를 붙여 안내한다.
 */
function loginLocation(unauthorizedCode: string | null, hadMember: boolean): string {
  const shouldExplain =
    unauthorizedCode !== null && (hadMember || unauthorizedCode === errorCodes.authInvalidCredential)
  return shouldExplain ? `${routes.login}?error=${unauthorizedCode}` : routes.login
}

function Gate({ children }: { children: ReactNode }) {
  return (
    <div className="login" style={{ minHeight: '100vh', padding: 24 }}>
      <div style={{ width: 'min(520px, 100%)' }}>{children}</div>
    </div>
  )
}
