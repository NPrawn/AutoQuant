import { useId } from 'react'

import { pickerLabel } from '../strategy/lineage'
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
        <select
          id={`${id}-strategy`}
          value={strategyId}
          disabled={pending || disabled}
          onChange={(event) => onChange(event.target.value)}
        >
          {strategies.map((strategy) => (
            <option key={strategy.id} value={strategy.id}>
              {pickerLabel(strategy)}
            </option>
          ))}
        </select>
      </div>
      <button className="btn primary" type="button" disabled={pending || disabled} onClick={onSubmit}>
        {pending ? '제출 중…' : '이 전략으로 제출'}
      </button>
      <span className="workflow-meta">
        <b>제출 횟수에 제한이 없습니다.</b> 낼 때마다 순위에 따로 오릅니다.
        {submission && ` · 마지막 제출 ${formatDateTime(submission.submittedAt)}`} · 채점용 데이터는 마감 후 공개
      </span>
    </div>
  )
}
