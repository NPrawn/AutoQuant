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
 * 목업 31·36 의 제출 패널. 전략을 고르고 누르는 것이 전부다.
 *
 * 횟수 제한이 없어 잘못 내도 다시 내면 되므로 곁들인 문구를 마지막 제출 시각 하나로 줄였다.
 * 동결과 채점 데이터 공개 시점은 제출 확인 모달이 한 번 말한다. 제출 기간이 아니면 눌러도 서버가
 * 막으므로 버튼을 미리 잠근다.
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
      {submission && (
        <span className="workflow-meta">마지막 제출 {formatDateTime(submission.submittedAt)}</span>
      )}
    </div>
  )
}
