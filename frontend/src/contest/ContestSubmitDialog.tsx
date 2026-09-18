import { useId, useState } from 'react'

import { ConfirmDialog } from '../components/ConfirmDialog'
import type { Strategy } from '../strategy/types'
import { formatDateTime } from './labels'
import type { ContestRoundDetailResponse } from './types'

type ContestSubmitDialogProps = {
  open: boolean
  round: ContestRoundDetailResponse
  strategy: Strategy | null
  pending: boolean
  onConfirm: () => void
  onCancel: () => void
}

/*
 * 목업 36 의 제출 확인. 제출하면 그 시점 전략이 동결되고 교체 횟수가 하나 줄어든다.
 * 같은 전략을 다시 내도 횟수에 들어가므로 무엇이 줄어드는지 숫자로 보인다.
 */
export function ContestSubmitDialog({ open, round, strategy, pending, onConfirm, onCancel }: ContestSubmitDialogProps) {
  const agreeId = useId()
  const [agreed, setAgreed] = useState(false)
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    setAgreed(false)
  }

  const remaining = round.mySubmission?.remainingSubmissions ?? round.maxSubmissions

  return (
    <ConfirmDialog
      open={open}
      title={`${round.title}에 제출할까요?`}
      description={
        <>
          제출하면 <b>{strategy?.name ?? '선택한 전략'}</b>이 이 시점 그대로 동결됩니다. 이후 전략을 수정해도 이번 회차
          성적은 바뀌지 않습니다.
        </>
      }
      detail={
        <>
          <div className="krow">
            <span>제출 전략</span>
            <span>{strategy?.name ?? '—'}</span>
          </div>
          <div className="krow">
            <span>채점 데이터</span>
            <span>일반 · 상승 · 하락 · 횡보 (마감 후 공개)</span>
          </div>
          <div className="krow">
            <span>남은 제출</span>
            <span className="mono">
              {remaining} / {round.maxSubmissions}회 → {Math.max(remaining - 1, 0)} / {round.maxSubmissions}회
            </span>
          </div>
          <div className="krow">
            <span>제출 마감</span>
            <span>{formatDateTime(round.submissionEndsAt)}</span>
          </div>
          <label className="agree" htmlFor={agreeId}>
            <input
              id={agreeId}
              type="checkbox"
              checked={agreed}
              disabled={pending}
              onChange={(event) => setAgreed(event.target.checked)}
            />{' '}
            같은 전략을 다시 제출해도 횟수가 줄어드는 것을 확인했으며 이 버전으로 제출합니다.
          </label>
        </>
      }
      confirmLabel={pending ? '제출 중…' : '제출'}
      confirmDisabled={!agreed}
      pending={pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  )
}
