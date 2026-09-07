import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'

import { routes } from '../app/routes'
import { fetchAuthorizeUrl, fetchCurrentMember, requestLogout } from './api'
import { toMember } from './types'

/* 로그인 여부의 유일한 근거. 쿠키는 읽을 수 없으니 이 쿼리 결과가 곧 인증 상태다. */
export const currentMemberQueryKey = ['auth', 'me'] as const

export function useCurrentMember() {
  return useQuery({
    queryKey: currentMemberQueryKey,
    queryFn: fetchCurrentMember,
    select: (response) => (response ? toMember(response) : null),
    staleTime: 5 * 60 * 1000,
  })
}

/*
 * 인가 URL 을 받아 SSAFY 로 보낸다. 돌아올 때는 백엔드 콜백이 쿠키를 심고 프론트
 * 루트로 302 리다이렉트하므로 여기서 결과를 받을 일은 없다.
 */
export function useStartLogin() {
  return useMutation({
    mutationFn: fetchAuthorizeUrl,
    onSuccess: ({ authorizeUrl }) => {
      window.location.assign(authorizeUrl)
    },
  })
}

/* 로그아웃하면 다른 회원의 데이터가 캐시에 남지 않게 전부 비우고 로그인 안 됨으로 둔다. */
export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: requestLogout,
    onSuccess: () => {
      queryClient.clear()
      queryClient.setQueryData(currentMemberQueryKey, null)
      navigate(routes.login, { replace: true })
    },
  })
}
