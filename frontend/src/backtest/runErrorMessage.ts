import { isApiError } from '../api/error.ts'
import type { ErrorDetailRow } from '../components/StateBlock'
import type { BacktestFailureCode } from './types'

/* 백테스트 실행 실패를 화면 문장으로 바꾼다. 400 은 백엔드가 필드별로 보내므로 항목으로 풀어 보인다. */
export type RunErrorView = {
  title: string
  description: string
  details: ErrorDetailRow[]
}

const fieldLabels: Array<[prefix: string, label: string]> = [
  ['strategyId', '전략'],
  ['periodStart', '시작일'],
  ['periodEnd', '종료일'],
  ['initialCapital', '초기자산'],
  ['feeRate', '수수료율'],
  ['slippageBps', '슬리피지'],
  ['executionPriceRule', '체결가 규칙'],
  ['engineVersion', '엔진 버전'],
  ['marketDataVersion', '시장 데이터 버전'],
  ['quoteCurrency', '기준 통화'],
]

function labelForField(field: string): string {
  return fieldLabels.find(([prefix]) => field.startsWith(prefix))?.[1] ?? field
}

export function describeRunError(error: unknown): RunErrorView {
  if (isApiError(error) && error.status === 400) {
    const issues = error.response?.validationErrors ?? []
    return {
      title: '백테스트를 시작하지 못했습니다',
      description:
        issues.length > 0
          ? `${issues.length}개 항목이 실행 규칙에 맞지 않습니다. 고치면 다시 실행할 수 있습니다.`
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
  return {
    title: '백테스트를 시작하지 못했습니다',
    description: `${isApiError(error) ? error.message : '서버에 연결할 수 없습니다.'} 잠시 후 다시 시도해 주세요. 입력한 조건은 그대로 남아 있습니다.`,
    details: [],
  }
}

/*
 * 계산이 실패한 경우. 목업 24 처럼 왜 멈췄는지와 다음 행동을 함께 보여 준다.
 * 원인은 서버가 failureCode 로 알려 주므로 화면이 추측하지 않는다.
 */
export function describeFailedRun(failureCode: BacktestFailureCode | null): RunErrorView {
  if (failureCode === 'INSUFFICIENT_CANDLES') {
    return {
      title: '평가 기간의 캔들이 부족해 계산을 멈췄습니다',
      description:
        '후보군 종목 중 일부가 요청한 시작일보다 늦게 상장되어 같은 기준으로 순위를 매길 수 없습니다. 평가 기간을 좁혀 다시 실행하세요.',
      details: [],
    }
  }
  if (failureCode === 'EMPTY_UNIVERSE') {
    return {
      title: '후보에 남는 종목이 없었습니다',
      description:
        '평가 기간 내내 조건을 통과한 종목이 하나도 없었습니다. 후보 조건을 느슨하게 하거나 순위 기준을 바꿔 다시 실행하세요.',
      details: [],
    }
  }
  if (failureCode === 'ENGINE_ERROR') {
    return {
      title: '계산 중 오류가 발생했습니다',
      description: '엔진이 이 전략을 끝까지 계산하지 못했습니다. 같은 조건으로 다시 실행해도 같은 지점에서 멈추면 알려 주세요.',
      details: [],
    }
  }
  return {
    title: '계산을 마치지 못했습니다',
    description:
      '평가 기간의 캔들이 부족하거나 계산 중 오류가 발생했습니다. 기간을 좁혀 다시 실행하거나 전략의 조건을 확인해 주세요.',
    details: [],
  }
}
