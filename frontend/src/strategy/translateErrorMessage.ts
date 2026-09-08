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

export function describeTranslateError(error: unknown): string {
  if (!isApiError(error)) {
    return fallbackMessage
  }
  if (error.code && messagesByCode[error.code]) {
    return messagesByCode[error.code]
  }
  if (error.status === 400) {
    const reasons = error.response?.validationErrors.map((issue) => issue.reason) ?? []
    return reasons.length > 0 ? `입력을 확인해 주세요. ${reasons.join(' ')}` : '입력을 확인해 주세요.'
  }
  return fallbackMessage
}
