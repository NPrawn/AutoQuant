import { Link } from 'react-router'

import { to } from '../app/routes'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'

type StrategyPickerProps = {
  strategies: Strategy[]
  selectedId: string
  disabled: boolean
  onChange: (strategyId: string) => void
}

/* 목업 10 의 상단 패널. 어떤 전략을 검증하는지 고르고 그 구성을 한 줄로 확인한다. */
export function StrategyPicker({ strategies, selectedId, disabled, onChange }: StrategyPickerProps) {
  const selected = strategies.find((strategy) => strategy.id === selectedId) ?? null

  return (
    <div className="workflow-panel">
      <div className="workflow-field">
        <label htmlFor="backtestStrategy">사용할 전략</label>
        <select
          id="backtestStrategy"
          value={selectedId}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        >
          {strategies.map((strategy) => (
            <option key={strategy.id} value={strategy.id}>
              {strategy.name}
            </option>
          ))}
        </select>
      </div>
      {selected ? (
        <Link className="btn" to={to.strategyDetail(selected.id)}>
          전략 확인
        </Link>
      ) : (
        <span />
      )}
      <span className="workflow-meta">
        {selected ? `v1 · 저장됨 · ${summarizeDsl(selected.dsl)}` : '전략을 선택해 주세요'}
      </span>
    </div>
  )
}
