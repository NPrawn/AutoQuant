import { errorCodes } from '../api/errorCodes'

/*
 * 로그인 콜백이 실패하면 백엔드가 /login?error={코드} 로 돌려보내고, 세션이 끊기면
 * RequireAuth 가 같은 형식으로 보낸다. 사용자에게 보일 문장은 여기서만 정한다.
 */
const messagesByCode: Record<string, string> = {
  [errorCodes.authInvalidAuthorizationCode]: '로그인 정보를 확인할 수 없습니다. 다시 시도해 주세요.',
  [errorCodes.authConsentRequired]: 'SSAFY 정보 제공에 동의해야 로그인할 수 있습니다.',
  [errorCodes.authAuthorizationCodeRejected]: '로그인 시간이 지났습니다. 다시 시도해 주세요.',
  [errorCodes.authCredentialRequired]: '로그인이 필요합니다. 다시 로그인해 주세요.',
  [errorCodes.authInvalidCredential]: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
  [errorCodes.authMemberNotActive]: '로그인할 수 없는 계정 상태입니다.',
  [errorCodes.authProviderUnavailable]: 'SSAFY 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
}

const fallbackMessage = '로그인에 실패했습니다. 다시 시도해 주세요.'

/* SSAFY 로 보내기 전, 인가 URL 조회 자체가 실패했을 때. 백엔드가 꺼져 있을 때 주로 본다. */
export const loginStartFailedMessage = 'SSAFY 로그인 페이지로 이동할 수 없습니다. 잠시 후 다시 시도해 주세요.'

export function describeLoginError(code: string | null): string | null {
  if (!code) {
    return null
  }
  return messagesByCode[code] ?? fallbackMessage
}
