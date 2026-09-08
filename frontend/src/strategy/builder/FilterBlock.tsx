import { Fragment } from 'react'

import { conditionLimit, type ConditionDraft, type SimpleConditionPatch } from '../draft'
import { logicLabels } from '../labels'
import type { Logic } from '../types'
import { ConditionRow } from './ConditionRow'

type FilterBlockProps = {
  conditions: ConditionDraft[]
  logic: Logic
  onAdd: () => void
  onChange: (id: string, patch: SimpleConditionPatch) => void
  onRemove: (id: string) => void
}

/*
 * 블록 01. 후보 조건 목록. 조건 사이의 논리는 목업처럼 고정 표시만 한다. AI 가 OR 을 보내면
 * 그대로 보여 주되 화면에서 바꾸는 컨트롤은 두지 않는다. 스크리닝은 조건을 쌓아 좁히는 작업이다.
 */
export function FilterBlock({ conditions, logic, onAdd, onChange, onRemove }: FilterBlockProps) {
  const full = conditions.length >= conditionLimit

  return (
    <div className="blk sig" data-cat="signal">
      <div className="blk-title">
        <span className="blk-index">01</span>
        <div>
          <span className="bt">어떤 코인을 후보로 남길까요?</span>
        </div>
      </div>
      <div className="blk-body">
        <div>
          {conditions.length === 0 && (
            <p className="goal-note" style={{ margin: '0 0 12px' }}>
              조건이 없으면 저장할 수 없습니다. 후보 조건을 하나 이상 넣어 주세요.
            </p>
          )}
          {conditions.map((condition, index) => (
            <Fragment key={condition.id}>
              {index > 0 && (
                <div className="logic-row">
                  <span className="logic-fixed">{logicLabels[logic]}</span>
                </div>
              )}
              {condition.kind === 'simple' ? (
                <ConditionRow
                  condition={condition}
                  index={index}
                  onChange={(patch) => onChange(condition.id, patch)}
                  onRemove={() => onRemove(condition.id)}
                />
              ) : (
                <div className="cond" role="group" aria-label={`조건 ${index + 1}`}>
                  <span className="goal-note" style={{ gridColumn: '1 / -1', margin: 0 }}>
                    이 조건은 화면에서 편집할 수 없는 형태라 그대로 유지합니다.
                  </span>
                  <button className="cond-del" type="button" aria-label={`조건 ${index + 1} 삭제`} onClick={() => onRemove(condition.id)}>
                    ×
                  </button>
                </div>
              )}
            </Fragment>
          ))}
        </div>
        <button className="add-cond" type="button" onClick={onAdd} disabled={full} title={full ? `조건은 ${conditionLimit}개까지 둘 수 있습니다` : undefined}>
          ＋ 조건 추가
        </button>
      </div>
    </div>
  )
}
