import { isApiError } from '../api/error'
import type { ErrorDetailRow } from '../components/StateBlock'

/* 모의투자 시작·정지 실패를 화면 문장으로 바꾼다. 백엔드 오류 코드와 상태별로 다음 행동이 다르다. */
export type SimulationErrorView = {
  title: string
  description: string
  details: ErrorDetailRow[]
}

const fieldLabels: Array<[prefix: string, label: string]> = [
  ['strategyId', '전략'],
  ['initialCapital', '초기 모의자산'],
  ['feeRate', '수수료율'],
  ['slippageBps', '슬리피지'],
  ['executionPriceRule', '체결가 규칙'],
  ['engineVersion', '엔진 버전'],
]

function labelForField(field: string): string {
  return fieldLabels.find(([prefix]) => field.startsWith(prefix))?.[1] ?? field
}

export function describeLaunchError(error: unknown): SimulationErrorView {
  if (isApiError(error) && error.status === 400) {
    const issues = error.response?.validationErrors ?? []
    return {
      title: '모의투자를 시작하지 못했습니다',
      description:
        issues.length > 0
          ? `${issues.length}개 항목이 실행 규칙에 맞지 않습니다. 고치면 다시 시작할 수 있습니다.`
          : `${error.message} 조건을 확인해 주세요.`,
      details: issues.map((issue) => ({ label: labelForField(issue.field), value: issue.reason })),
    }
  }
  if (isApiError(error) && error.status === 404) {
    return {
      title: '전략을 찾을 수 없습니다',
      description: '삭제된 전략이거나 접근할 수 없는 전략입니다. 목록에서 다시 선택해 주세요.',
      details: [],
    }
  }
  if (isApiError(error) && error.status === 409) {
    return {
      title: '지금 상태에서는 시작할 수 없습니다',
      description: '이미 운용 중이거나 실패로 끝난 실행입니다. 목록에서 상태를 확인해 주세요.',
      details: [],
    }
  }
  return {
    title: '모의투자를 시작하지 못했습니다',
    description: `${isApiError(error) ? error.message : '서버에 연결할 수 없습니다.'} 잠시 후 다시 시도해 주세요. 입력한 조건은 그대로 남아 있습니다.`,
    details: [],
  }
}

/* 정지와 재시작은 상태 전이라 409 가 대부분이다. 화면이 알던 상태가 낡았다는 뜻이라 다시 조회하면 된다. */
export function describeTransitionError(error: unknown, action: '정지' | '재시작'): string {
  if (isApiError(error) && error.status === 409) {
    return `지금 상태에서는 ${action}할 수 없습니다. 화면을 새로 고쳐 현재 상태를 확인해 주세요.`
  }
  return `${action}하지 못했습니다. ${isApiError(error) ? error.message : '서버에 연결할 수 없습니다.'}`
}
