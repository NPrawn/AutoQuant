import { isApiError } from '../api/error'
import type { ErrorDetailRow } from '../components/StateBlock'

/*
 * 전략 저장 실패를 화면 문장으로 바꾼다. 400 은 백엔드 Bean Validation 이 필드별로 보내므로
 * 항목으로 풀어 보이고, 그 밖의 실패는 편집 내용이 남아 있다는 점을 먼저 알린다.
 */
export type SaveErrorView = {
  title: string
  description: string
  details: ErrorDetailRow[]
}

/* 백엔드 필드 경로를 사용자가 아는 이름으로. 접두사만 맞으면 하위 경로도 같은 이름을 쓴다. */
const fieldLabels: Array<[prefix: string, label: string]> = [
  ['name', '전략 이름'],
  ['description', '설명'],
  ['strategyDsl.filters', '후보 조건'],
  ['strategyDsl.ranking', '순위 기준'],
  ['strategyDsl.selection', '투자 종목 수'],
  ['strategyDsl.rebalance', '재선정 주기'],
  ['strategyDsl.timeframe', '봉 주기'],
  ['strategyDsl', '전략 구성'],
]

function labelForField(field: string): string {
  return fieldLabels.find(([prefix]) => field.startsWith(prefix))?.[1] ?? field
}

export function describeSaveError(error: unknown): SaveErrorView {
  const keepNote = '편집한 내용은 그대로 남아 있습니다.'

  if (isApiError(error) && error.status === 400) {
    const issues = error.response?.validationErrors ?? []
    return {
      title: '저장하지 못했습니다',
      description:
        issues.length > 0
          ? `${issues.length}개 항목이 저장 규칙에 맞지 않습니다. 고치면 저장되며 ${keepNote}`
          : `입력이 저장 규칙에 맞지 않습니다. ${keepNote}`,
      details: issues.map((issue) => ({ label: labelForField(issue.field), value: issue.reason })),
    }
  }

  return {
    title: '저장하지 못했습니다',
    description: `${isApiError(error) ? error.message : '서버에 연결할 수 없습니다.'} 잠시 후 다시 시도해 주세요. ${keepNote}`,
    details: [],
  }
}
