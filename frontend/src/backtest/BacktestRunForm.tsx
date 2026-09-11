import { useId } from 'react'

import { timeframeLabels } from '../strategy/labels'
import type { Strategy } from '../strategy/types'
import { formatKrw } from './labels'
import {
  applyPreset,
  detectPreset,
  feeRate,
  setInitialCapital,
  setPeriodEnd,
  setPeriodStart,
  slippageBps,
  universeSize,
  validateRunForm,
  type BacktestRunForm as RunForm,
  type CandleCoverage,
  type PresetRange,
} from './runForm'

type BacktestRunFormProps = {
  form: RunForm
  strategy: Strategy | null
  today: Date
  /* 캔들 보유 구간. 아직 읽는 중이면 undefined, 봉이 없으면 null 이다. */
  coverage: CandleCoverage | null | undefined
  pending: boolean
  showIssues: boolean
  onChange: (form: RunForm) => void
  onSubmit: () => void
}

const presets: Array<{ id: PresetRange; label: string }> = [
  { id: 'all', label: '보유 전체' },
  { id: '3m', label: '최근 3개월' },
  { id: '1m', label: '최근 1개월' },
]

/* 목업 10 의 좌측 카드. 봉 주기는 전략 DSL 에서 정해져 오므로 읽기 전용으로 보여 준다. */
export function BacktestRunForm({ form, strategy, today, coverage, pending, showIssues, onChange, onSubmit }: BacktestRunFormProps) {
  const id = useId()
  const knownCoverage = coverage ?? null
  const issues = validateRunForm(form, today, knownCoverage)
  const activePreset = detectPreset(form, today, knownCoverage)
  const issueFor = (field: string) => (showIssues ? issues.find((issue) => issue.field === field) : undefined)
  const periodIssue = issueFor('periodStart') ?? issueFor('periodEnd')
  const capitalIssue = issueFor('initialCapital')

  const changeCapital = (raw: string) => {
    const digits = raw.replace(/[^\d]/g, '')
    onChange(setInitialCapital(form, digits === '' ? Number.NaN : Number(digits)))
  }

  return (
    <div className="card pad">
      <div className="fld">
        <label htmlFor={`${id}-start`}>평가 기간</label>
        <div className="presets" role="group" aria-label="평가 기간 프리셋">
          {presets.map((preset) => (
            <button
              key={preset.id}
              type="button"
              className={preset.id === activePreset ? 'on' : undefined}
              aria-pressed={preset.id === activePreset}
              disabled={pending}
              onClick={() => onChange(applyPreset(form, preset.id, today, knownCoverage))}
            >
              {preset.label}
            </button>
          ))}
        </div>
        <div className="dr">
          <input
            id={`${id}-start`}
            type="date"
            value={form.periodStart}
            disabled={pending}
            aria-label="시작일"
            aria-invalid={issueFor('periodStart') !== undefined}
            onChange={(event) => onChange(setPeriodStart(form, event.target.value))}
          />
          <span style={{ color: 'var(--ink-3)' }} aria-hidden="true">
            →
          </span>
          <input
            type="date"
            value={form.periodEnd}
            disabled={pending}
            aria-label="종료일"
            aria-invalid={issueFor('periodEnd') !== undefined}
            onChange={(event) => onChange(setPeriodEnd(form, event.target.value))}
          />
        </div>
        {periodIssue ? (
          <p className="field-note crit">{periodIssue.message}</p>
        ) : (
          <p className="field-note">
            {coverage === undefined
              ? '캔들 보유 구간을 확인하는 중…'
              : coverage === null
                ? '보유 캔들을 확인할 수 없어 기간 하한 없이 실행합니다.'
                : `캔들은 ${coverage.start} ~ ${coverage.end} 구간에 있습니다. 그 밖은 평가되지 않습니다.`}
          </p>
        )}
      </div>

      <div className="fld">
        <label htmlFor={`${id}-timeframe`}>데이터 주기</label>
        <input
          id={`${id}-timeframe`}
          type="text"
          readOnly
          value={strategy ? timeframeLabels[strategy.dsl.timeframe] : '전략을 선택하면 표시됩니다'}
        />
        <p className="field-note">전략에 저장된 주기를 그대로 씁니다.</p>
      </div>

      <div className="fld">
        <label htmlFor={`${id}-capital`}>초기자산 (KRW)</label>
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

      <p className="goal-note" style={{ margin: '2px 0 14px' }}>
        후보군은 <b>업비트 KRW 거래대금 상위 {universeSize}</b>으로 고정입니다. 비용은 수수료{' '}
        {(feeRate * 100).toFixed(2)}%와 슬리피지 {slippageBps}bp를 적용합니다.
      </p>

      <button className="btn primary" type="button" style={{ width: '100%' }} disabled={pending} onClick={onSubmit}>
        {pending ? '실행 중…' : '백테스트 실행'}
      </button>
    </div>
  )
}
