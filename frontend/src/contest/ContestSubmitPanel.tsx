import { useId } from 'react'

import { StrategySelect } from '../components/StrategySelect'
import type { Strategy } from '../strategy/types'
import { formatDateTime } from './labels'
import type { ContestSubmissionResponse } from './types'

type ContestSubmitPanelProps = {
  strategies: Strategy[]
  strategyId: string
  submission: ContestSubmissionResponse | null
  pending: boolean
  disabled: boolean
  onChange: (strategyId: string) => void
  onSubmit: () => void
}

/*
 * 목업 31·36 의 제출 패널. 제출 횟수에 제한이 없고 낼 때마다 별개의 제출로 순위에 오른다.
 * 제출 기간이 아니면 눌러도 서버가 막으므로 버튼을 미리 잠근다.
 */
export function ContestSubmitPanel({
  strategies,
  strategyId,
  submission,
  pending,
  disabled,
  onChange,
  onSubmit,
}: ContestSubmitPanelProps) {
  const id = useId()

  return (
    <div className="card submit-panel">
      <div className="workflow-field">
        <label htmlFor={`${id}-strategy`}>제출할 전략</label>
        <StrategySelect id={`${id}-strategy`} strategies={strategies} value={strategyId} disabled={pending || disabled} onChange={onChange} />
      </div>
      <button className="btn primary" type="button" disabled={pending || disabled} onClick={onSubmit}>
        {pending ? '제출 중…' : '이 전략으로 제출'}
      </button>
      <span className="workflow-meta">
        제출 횟수 제한 없음 · 채점용 데이터는 마감 후 공개
        {submission && ` · 마지막 제출 ${formatDateTime(submission.submittedAt)}`}
      </span>
    </div>
  )
}
