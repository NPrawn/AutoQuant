/*
 * backend/api 의 ApiErrorResponse 와 같은 모양이다. 인증 필터가 직접 쓰는 401 응답도
 * 같은 형식이라 모든 실패를 이 타입 하나로 받는다.
 */
export type ApiValidationError = {
  field: string
  reason: string
}

export type ApiErrorResponse = {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  validationErrors: ApiValidationError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly response: ApiErrorResponse | null

  constructor(status: number, response: ApiErrorResponse | null) {
    super(response?.message ?? `API 요청이 ${status} 상태로 실패했습니다.`)
    this.name = 'ApiError'
    this.status = status
    this.code = response?.code ?? null
    this.response = response
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
