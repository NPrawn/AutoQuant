import { request } from '../api/client'
import { isApiError } from '../api/error'
import { errorCodes } from '../api/errorCodes'
import type { AuthorizeUrlResponse, CurrentMemberResponse, MemberResponse } from './types'

export function fetchAuthorizeUrl(): Promise<AuthorizeUrlResponse> {
  return request<AuthorizeUrlResponse>('/auth/ssafy/authorize-url')
}

/*
 * 401 은 "로그인하지 않았다"는 정상 답이므로 오류 코드를 담아 돌려준다. 서버 장애
 * 같은 그 외 실패는 그대로 던져서 화면이 로그인 안 됨과 구분할 수 있게 한다.
 */
export async function fetchCurrentMember(): Promise<CurrentMemberResponse> {
  try {
    const member = await request<MemberResponse>('/members/me')
    return { member, unauthorizedCode: null }
  } catch (error) {
    if (isApiError(error) && error.status === 401) {
      return { member: null, unauthorizedCode: error.code ?? errorCodes.authCredentialRequired }
    }
    throw error
  }
}

/* 토큰 쿠키가 httpOnly 라 프론트가 지울 수 없어 서버가 만료시킨다. */
export function requestLogout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' })
}
