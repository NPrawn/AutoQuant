import { isApiError } from '../api/error'
import { errorCodes } from '../api/errorCodes'

/*
 * 변환 실패를 전략봇이 사용자에게 말하는 문장. 코드별 대응은 여기에만 둔다.
 * 401 은 여기까지 오지 않는다. QueryClient 가 세션 만료로 처리한다.
 */
const messagesByCode: Record<string, string> = {
  [errorCodes.aiTranslationFailed]: '이 문장은 전략으로 바꾸지 못했습니다. 어떤 지표를 어떻게 볼지 조금 더 구체적으로 적어 주세요.',
  [errorCodes.aiTranslationQuotaExceeded]: '변환 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.',
  [errorCodes.aiTranslationInvalidResponse]: 'AI 서버 응답을 처리하지 못했습니다. 다시 한 번 보내 주세요.',
  [errorCodes.aiTranslationUnavailable]: 'AI 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. 블록은 직접 편집할 수 있습니다.',
  [errorCodes.aiTranslationTimeout]: 'AI 서버 응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.',
}

const fallbackMessage = '변환 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.'

/*
 * 공개·복제 실패를 알리는 문장. 두 동작 모두 없는 전략이면 같은 404 로 오므로
 * 무엇을 하려다 실패했는지에 따라 다르게 읽힌다.
 */
export function describeVisibilityError(error: unknown): string {
  if (isApiError(error) && error.status === 404) {
    return '전략을 찾을 수 없습니다. 삭제되었을 수 있습니다.'
  }
  return '공개 여부를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function describeCopyError(error: unknown): string {
  if (isApiError(error) && error.status === 404) {
    return '복제할 전략을 찾을 수 없습니다. 삭제되었거나 비공개로 바뀌었을 수 있습니다.'
  }
  return '전략을 복제하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function describeTranslateError(error: unknown): string {
  if (!isApiError(error)) {
    return fallbackMessage
  }
  if (error.code && messagesByCode[error.code]) {
    return messagesByCode[error.code]
  }
  if (error.status === 400) {
    const reasons = error.response?.validationErrors?.map((issue) => issue.reason) ?? []
    return reasons.length > 0 ? `입력을 확인해 주세요. ${reasons.join(' ')}` : '입력을 확인해 주세요.'
  }
  return fallbackMessage
}
