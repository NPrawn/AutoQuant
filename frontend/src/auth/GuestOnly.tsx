import { Navigate, Outlet } from 'react-router'

import { routes } from '../app/routes'
import { useCurrentMember } from './queries'

/* 이미 로그인한 사용자가 로그인 화면에 오면 홈으로 보낸다. 확인 중에는 로그인 화면을 그대로 보인다. */
export function GuestOnly() {
  const { data } = useCurrentMember()

  if (data?.member) {
    return <Navigate to={routes.home} replace />
  }
  return <Outlet />
}
