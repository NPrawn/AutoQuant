import { Link } from 'react-router'

import { to } from '../app/routes'
import { StrategySelect } from '../components/StrategySelect'
import { versionLabel } from '../strategy/lineage'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'

type StrategyPickerProps = {
  /* 드롭다운에 올릴 전략. 최신 버전만 오는지는 호출한 화면이 pickerOptions 로 정한다. */
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
        <StrategySelect id="backtestStrategy" strategies={strategies} value={selectedId} disabled={disabled} onChange={onChange} />
      </div>
      {selected ? (
        <Link className="btn" to={to.strategyDetail(selected.id)}>
          전략 확인
        </Link>
      ) : (
        <span />
      )}
      <span className="workflow-meta">
        {selected ? `${versionLabel(selected.version)} · 저장됨 · ${summarizeDsl(selected.dsl)}` : '전략을 선택해 주세요'}
      </span>
    </div>
  )
}
