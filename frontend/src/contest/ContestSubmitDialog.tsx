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
 * 목업 36 의 제출 확인. 제출하면 그 시점 전략이 동결되고 별개의 제출로 순위에 오른다.
 * 횟수 제한이 없어 무엇이 줄어드는지 보일 것이 없고, 대신 동결된다는 사실을 확인받는다.
 */
export function ContestSubmitDialog({ open, round, strategy, pending, onConfirm, onCancel }: ContestSubmitDialogProps) {
  const agreeId = useId()
  const [agreed, setAgreed] = useState(false)
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    setAgreed(false)
  }

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
            이 제출이 순위에 따로 오르고 이후 전략을 수정해도 바뀌지 않는 것을 확인했습니다.
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
