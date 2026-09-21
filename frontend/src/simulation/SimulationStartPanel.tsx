import { useId } from 'react'

import { formatKrw } from '../backtest/labels'
import { pickerLabel } from '../strategy/lineage'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'
import { setInitialCapital, setStrategyId, validateStartForm, type SimulationStartForm } from './startForm'

type SimulationStartPanelProps = {
  strategies: Strategy[]
  form: SimulationStartForm
  pending: boolean
  showIssues: boolean
  onChange: (form: SimulationStartForm) => void
  onSubmit: () => void
}

/* 목업 16 의 상단 패널. 전략과 초기 모의자산을 고르면 확인 모달이 열린다. */
export function SimulationStartPanel({ strategies, form, pending, showIssues, onChange, onSubmit }: SimulationStartPanelProps) {
  const id = useId()
  const selected = strategies.find((strategy) => strategy.id === form.strategyId) ?? null
  const issues = showIssues ? validateStartForm(form) : []
  const capitalIssue = issues.find((issue) => issue.field === 'initialCapital')

  const changeCapital = (raw: string) => {
    const digits = raw.replace(/[^\d]/g, '')
    onChange(setInitialCapital(form, digits === '' ? Number.NaN : Number(digits)))
  }

  return (
    <div className="workflow-panel">
      <div className="workflow-field">
        <label htmlFor={`${id}-strategy`}>사용할 전략</label>
        <select id={`${id}-strategy`} value={form.strategyId} disabled={pending} onChange={(event) => onChange(setStrategyId(form, event.target.value))}>
          {strategies.map((strategy) => (
            <option key={strategy.id} value={strategy.id}>
              {pickerLabel(strategy)}
            </option>
          ))}
        </select>
      </div>
      <div className="workflow-field">
        <label htmlFor={`${id}-capital`}>초기 모의자산 (KRW)</label>
        <input
          id={`${id}-capital`}
          className="mono"
          type="text"
          inputMode="numeric"
          value={Number.isNaN(form.initialCapital) ? '' : formatKrw(form.initialCapital)}
          disabled={pending}
          aria-invalid={capitalIssue !== undefined}
          onChange={(event) => changeCapital(event.target.value)}
        />
        {capitalIssue && <p className="field-note crit">{capitalIssue.message}</p>}
      </div>
      <button className="btn primary" type="button" disabled={pending} onClick={onSubmit}>
        {pending ? '시작 중…' : '새 모의투자 시작'}
      </button>
      <span className="workflow-meta">{selected ? `v1 · ${summarizeDsl(selected.dsl)}` : '전략을 선택해 주세요'}</span>
    </div>
  )
}
