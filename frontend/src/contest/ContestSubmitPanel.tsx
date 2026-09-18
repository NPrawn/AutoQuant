import { useId } from 'react'

import type { Strategy } from '../strategy/types'
import { formatDateTime } from './labels'
import type { ContestSubmissionResponse } from './types'

type ContestSubmitPanelProps = {
  strategies: Strategy[]
  strategyId: string
  maxSubmissions: number
  submission: ContestSubmissionResponse | null
  pending: boolean
  disabled: boolean
  onChange: (strategyId: string) => void
  onSubmit: () => void
}

/*
 * 목업 31·36 의 제출 패널. 제출과 교체가 같은 동작이라 버튼 하나만 둔다. 남은 횟수가 0이거나
 * 제출 기간이 아니면 눌러도 서버가 막으므로 버튼을 미리 잠근다.
 */
export function ContestSubmitPanel({
  strategies,
  strategyId,
  maxSubmissions,
  submission,
  pending,
  disabled,
  onChange,
  onSubmit,
}: ContestSubmitPanelProps) {
  const id = useId()
  const remaining = submission?.remainingSubmissions ?? maxSubmissions
  const soldOut = remaining <= 0

  return (
    <div className="card submit-panel">
      <div className="workflow-field">
        <label htmlFor={`${id}-strategy`}>제출할 전략</label>
        <select
          id={`${id}-strategy`}
          value={strategyId}
          disabled={pending || disabled || soldOut}
          onChange={(event) => onChange(event.target.value)}
        >
          {strategies.map((strategy) => (
            <option key={strategy.id} value={strategy.id}>
              {strategy.name}
            </option>
          ))}
        </select>
      </div>
      <button className="btn primary" type="button" disabled={pending || disabled || soldOut} onClick={onSubmit}>
        {pending ? '제출 중…' : submission ? '이 전략으로 교체' : '이 전략으로 제출'}
      </button>
      <span className="workflow-meta">
        {soldOut && <b>제출 횟수를 모두 썼습니다. 마지막으로 제출한 전략으로 채점됩니다. · </b>}
        남은 제출{' '}
        <b>
          {remaining} / {maxSubmissions}회
        </b>
        {submission && ` · 마지막 제출 ${formatDateTime(submission.lastSubmittedAt)}`} · 채점용 데이터는 마감 후 공개
      </span>
    </div>
  )
}
