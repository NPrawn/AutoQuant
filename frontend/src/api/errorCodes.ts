/*
 * backend/api 의 ApiErrorCode 와 같은 값. 화면 코드는 이 상수만 쓰고 'AUTH-401-…'
 * 같은 문자열을 직접 비교하지 않는다. 백엔드에 코드가 늘면 여기에만 추가한다.
 */
export const errorCodes = {
  authInvalidAuthorizationCode: 'AUTH-400-001',
  authConsentRequired: 'AUTH-400-002',
  authAuthorizationCodeRejected: 'AUTH-401-001',
  authCredentialRequired: 'AUTH-401-002',
  authInvalidCredential: 'AUTH-401-003',
  authMemberNotActive: 'AUTH-403-001',
  authProviderUnavailable: 'AUTH-502-001',
} as const

export type ErrorCode = (typeof errorCodes)[keyof typeof errorCodes]
