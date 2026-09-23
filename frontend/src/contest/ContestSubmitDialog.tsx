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
 * 목업 36 의 제출 확인. 동결된다는 사실을 한 번 말하고 마감 시각을 보여 준다.
 *
 * 동의 체크박스를 뺐다. 제출 횟수가 5회로 막혀 있을 때는 한 번이 줄어드는 것을 확인받을 이유가
 * 있었지만, 제한이 없어진 뒤로는 잘못 내도 다시 내면 된다. 되돌릴 수 없는 동작이 아니다.
 */
export function ContestSubmitDialog({ open, round, strategy, pending, onConfirm, onCancel }: ContestSubmitDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      title={`${round.title}에 제출할까요?`}
      description={
        <>
          <b>{strategy?.name ?? '선택한 전략'}</b>이 이 시점 그대로 동결됩니다. 이후 전략을 수정해도 이번 회차 성적은
          바뀌지 않습니다.
        </>
      }
      detail={
        <>
          <div className="krow">
            <span>채점 데이터</span>
            <span>일반 · 상승 · 하락 · 횡보 (마감 후 공개)</span>
          </div>
          <div className="krow">
            <span>제출 마감</span>
            <span>{formatDateTime(round.submissionEndsAt)}</span>
          </div>
        </>
      }
      confirmLabel={pending ? '제출 중…' : '제출'}
      pending={pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  )
}
