import { useId, useState } from 'react'

import { formatKrw } from '../backtest/labels'
import { feeRate, slippageBps } from '../backtest/runForm'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { rebalanceMeta, timeframeLabels } from '../strategy/labels'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'
import type { SimulationStartForm } from './startForm'

type SimulationStartDialogProps = {
  open: boolean
  strategy: Strategy | null
  form: SimulationStartForm
  pending: boolean
  onConfirm: () => void
  onCancel: () => void
}

/*
 * 목업 17 의 확인 모달. 무엇을 어떤 조건으로 운용하는지 한 번 더 보이고, 동의를 받은 뒤에만
 * 시작한다. 모달이 닫히면 동의는 지운다. 다음에 열 때 다시 읽고 체크해야 한다.
 */
export function SimulationStartDialog({ open, strategy, form, pending, onConfirm, onCancel }: SimulationStartDialogProps) {
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
      title="모의투자 시작 확인"
      description={
        <>
          <b>{strategy?.name ?? '선택한 전략'}</b> 전략을 아래 조건으로 실제 시세에서 모의운용합니다.
        </>
      }
      detail={
        <>
          <div className="krow">
            <span>후보 조건 · 순위</span>
            <span>{strategy ? summarizeDsl(strategy.dsl) : '—'}</span>
          </div>
          <div className="krow">
            <span>판단 주기 · 재선정</span>
            <span>{strategy ? `${timeframeLabels[strategy.dsl.timeframe]} 종가 · ${rebalanceMeta[strategy.dsl.rebalance.period].label}` : '—'}</span>
          </div>
          <div className="krow">
            <span>비용 · 체결</span>
            <span>
              수수료 {(feeRate * 100).toFixed(2)}% · 슬리피지 {slippageBps}bp · 다음 봉 시가
            </span>
          </div>
          <div className="krow">
            <span>초기 모의자산</span>
            <span className="mono">{formatKrw(form.initialCapital)} KRW</span>
          </div>
          <label className="agree" htmlFor={agreeId}>
            <input id={agreeId} type="checkbox" checked={agreed} disabled={pending} onChange={(event) => setAgreed(event.target.checked)} />{' '}
            실제 자산이 아닌 모의자산으로 운용되며 이 성적은 랭킹에 반영되지 않는 것을 확인했습니다.
          </label>
        </>
      }
      confirmLabel={pending ? '시작 중…' : '모의투자 시작'}
      confirmDisabled={!agreed}
      pending={pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  )
}
