import { ApiError, type ApiErrorResponse } from './error'

/* Vite 프록시가 /api 를 백엔드로 넘기며 접두사를 지운다. 배포에서는 nginx 가 같은 역할을 한다. */
const API_BASE = '/api'

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/*
 * 모든 API 호출은 이 함수를 지난다. 토큰은 httpOnly 쿠키라 브라우저가 알아서 실어
 * 보내므로 여기서 헤더에 붙일 것이 없다. 실패는 ApiError 하나로 던진다.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal } = options

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorBody(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/* 프록시 장애처럼 JSON 이 아닌 오류 본문도 오므로 파싱 실패는 본문 없음으로 본다. */
async function readErrorBody(response: Response): Promise<ApiErrorResponse | null> {
  try {
    return (await response.json()) as ApiErrorResponse
  } catch {
    return null
  }
}
