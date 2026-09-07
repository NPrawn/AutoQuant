import type { QueryClient } from '@tanstack/react-query'

import { isApiError } from '../api/error'
import { errorCodes } from '../api/errorCodes'
import { currentMemberQueryKey, signedOut } from './queries'

/*
 * 어떤 API 든 401 을 돌려주면 세션이 끝난 것이다. 회원 쿼리를 "로그인 안 됨"으로
 * 바꾸면 RequireAuth 가 그 이유를 붙여 로그인 화면으로 보낸다. 화면마다 401 을
 * 따로 처리하지 않도록 QueryClient 의 캐시 단계에서 한 번만 잡는다.
 */
export function expireSessionOnUnauthorized(queryClient: QueryClient, error: unknown): void {
  if (!isApiError(error) || error.status !== 401) {
    return
  }
  queryClient.setQueryData(currentMemberQueryKey, signedOut(error.code ?? errorCodes.authCredentialRequired))
}
