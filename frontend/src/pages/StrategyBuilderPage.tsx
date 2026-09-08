import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'

import { routes } from '../app/routes'
import { readBuilderEntry } from '../strategy/builderEntry'
import { FilterBlock } from '../strategy/builder/FilterBlock'
import { RankingBlock } from '../strategy/builder/RankingBlock'
import { RebalanceBlock } from '../strategy/builder/RebalanceBlock'
import { StrategySummaryCard } from '../strategy/builder/StrategySummaryCard'
import {
  addCondition,
  createEmptyDraft,
  removeCondition,
  setName,
  setRanking,
  setRebalance,
  setTimeframe,
  updateCondition,
  validateDraft,
} from '../strategy/draft'
import { strategyNameMaxLength } from '../strategy/types'

/*
 * 전략 빌더(목업 19). 편집 상태는 draft.ts 의 순수 함수로만 바꾼다. 홈에서 넘어온 목표 문장은
 * 요약 카드에 보이고, 그 문장으로 블록을 채우는 변환 호출과 저장 호출은 다음 작업에서 연결한다.
 * base.css 가 #view-builder 로 블록 격자를 정하므로 목업의 id 를 그대로 둔다.
 */
export function StrategyBuilderPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const entry = readBuilderEntry(location.state)

  const [draft, setDraft] = useState(createEmptyDraft)
  const [showIssues, setShowIssues] = useState(false)
  const issues = validateDraft(draft)

  const handleSave = () => {
    setShowIssues(true)
  }

  return (
    <section className="view active" id="view-builder">
      <div className="page-head">
        <div>
          <span className="eyebrow">새 전략</span>
          <label htmlFor="strategyName" className="sr-only">
            전략 이름
          </label>
          <input
            id="strategyName"
            className="page-title-input"
            type="text"
            placeholder="전략 이름을 적어 주세요"
            maxLength={strategyNameMaxLength}
            value={draft.name}
            onChange={(event) => setDraft(setName(draft, event.target.value))}
            aria-invalid={showIssues && issues.some((issue) => issue.field === 'name')}
          />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn ghost sm" type="button" onClick={() => navigate(routes.strategies)}>
            취소
          </button>
          <button className="btn primary sm" type="button" onClick={handleSave}>
            전략 저장
          </button>
        </div>
      </div>

      <div className="edit-shell">
        <div>
          <div className="tabp on">
            <FilterBlock
              conditions={draft.conditions}
              logic={draft.logic}
              onAdd={() => setDraft(addCondition(draft))}
              onChange={(id, patch) => setDraft(updateCondition(draft, id, patch))}
              onRemove={(id) => setDraft(removeCondition(draft, id))}
            />
            <RankingBlock
              ranking={draft.ranking}
              timeframe={draft.timeframe}
              onRankingChange={(patch) => setDraft(setRanking(draft, patch))}
              onTimeframeChange={(timeframe) => setDraft(setTimeframe(draft, timeframe))}
            />
            <RebalanceBlock rebalance={draft.rebalance} onChange={(rebalance) => setDraft(setRebalance(draft, rebalance))} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
            <button className="btn ghost" type="button" onClick={() => navigate(routes.strategies)}>
              취소
            </button>
            <button className="btn primary" type="button" onClick={handleSave}>
              전략 저장
            </button>
          </div>
        </div>

        <aside className="edit-side">
          <StrategySummaryCard draft={draft} goal={entry?.prompt ?? null} issues={showIssues ? issues : []} />
        </aside>
      </div>
    </section>
  )
}
