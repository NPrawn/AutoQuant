import type { SimpleCondition, SimpleConditionPatch } from '../draft'
import { comparisonOperators, factorMeta, factorTypes, operatorSymbols } from '../labels'
import type { ComparisonOperator, FactorType } from '../types'

type ConditionRowProps = {
  condition: SimpleCondition
  index: number
  onChange: (patch: SimpleConditionPatch) => void
  onRemove: () => void
}

/* 빈 입력은 NaN 으로 두고 validateDraft 가 잡는다. 0 으로 바꾸면 사용자가 지운 사실을 잃는다. */
const numberOrNaN = (raw: string): number => (raw.trim() === '' ? Number.NaN : Number(raw))
const displayNumber = (value: number): string | number => (Number.isNaN(value) ? '' : value)

/* 목업 19 의 .cond 한 줄. 지표 · 기간 · 연산자 · 기준값 · 삭제 순서다. */
export function ConditionRow({ condition, index, onChange, onRemove }: ConditionRowProps) {
  const meta = factorMeta[condition.factor]
  const label = `조건 ${index + 1}`

  return (
    <div className="cond" role="group" aria-label={label}>
      <select
        aria-label={`${label} 지표`}
        value={condition.factor}
        onChange={(event) => onChange({ factor: event.target.value as FactorType })}
      >
        {factorTypes.map((factor) => (
          <option key={factor} value={factor}>
            {factorMeta[factor].label}
          </option>
        ))}
      </select>
      <input
        className="num"
        type="number"
        min={1}
        max={500}
        step={1}
        aria-label={`${label} 기간`}
        value={displayNumber(condition.period)}
        onChange={(event) => onChange({ period: numberOrNaN(event.target.value) })}
      />
      <select
        aria-label={`${label} 비교`}
        value={condition.operator}
        onChange={(event) => onChange({ operator: event.target.value as ComparisonOperator })}
      >
        {comparisonOperators.map((operator) => (
          <option key={operator} value={operator}>
            {operatorSymbols[operator]}
          </option>
        ))}
      </select>
      <div className="cond-unit">
        <input
          className="num"
          type="number"
          step={0.1}
          aria-label={`${label} 기준값`}
          value={displayNumber(condition.value)}
          onChange={(event) => onChange({ value: numberOrNaN(event.target.value) })}
        />
        {meta.unit && <em>{meta.unit}</em>}
      </div>
      <button className="cond-del" type="button" aria-label={`${label} 삭제`} onClick={onRemove}>
        ×
      </button>
    </div>
  )
}
