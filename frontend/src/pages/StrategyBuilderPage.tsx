import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'

import { routes } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { AssistantPanel } from '../strategy/assistant/AssistantPanel'
import {
  appendAssistantMessage,
  appendUserMessage,
  createConversation,
  describeStrategy,
  setPending,
  suggestedRequests,
} from '../strategy/assistant/conversation'
import { readBuilderEntry } from '../strategy/builderEntry'
import { FilterBlock } from '../strategy/builder/FilterBlock'
import { RankingBlock } from '../strategy/builder/RankingBlock'
import { RebalanceBlock } from '../strategy/builder/RebalanceBlock'
import { StrategySummaryCard } from '../strategy/builder/StrategySummaryCard'
import {
  addCondition,
  createEmptyDraft,
  draftFromDsl,
  dslFromDraft,
  removeCondition,
  setName,
  setRanking,
  setRebalance,
  setTimeframe,
  updateCondition,
  validateDraft,
  type StrategyDraft,
} from '../strategy/draft'
import { useTranslateStrategy } from '../strategy/queries'
import { describeTranslateError } from '../strategy/translateErrorMessage'
import { strategyNameMaxLength, type TranslateStrategyResponse } from '../strategy/types'

/* 홈에서 넘어온 첫 변환의 진행 상태. 성공하면 done, 실패하면 화면 위쪽에 안내와 재시도를 보인다. */
type InitialPhase = 'none' | 'pending' | 'failed' | 'done'

/*
 * 전략 빌더(목업 19). 편집 상태는 draft.ts 의 순수 함수로만 바꾼다. 홈에서 넘어온 목표 문장은
 * 진입 즉시 변환해 블록을 채우고, 전략봇의 요청은 지금 블록을 함께 보내 고쳐 받는다.
 * base.css 가 #view-builder 로 블록 격자를 정하므로 목업의 id 를 그대로 둔다.
 */
export function StrategyBuilderPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const entry = readBuilderEntry(location.state)

  const [draft, setDraft] = useState(createEmptyDraft)
  const [showIssues, setShowIssues] = useState(false)
  const issues = validateDraft(draft)

  /* 홈에서 온 문장은 첫 사용자 메시지로 두고 대기 상태로 시작한다. 변환 호출은 아래 effect 가 한다. */
  const [conversation, setConversation] = useState(() =>
    entry ? setPending(appendUserMessage(createConversation(), entry.prompt), true) : createConversation(),
  )
  const [initialPhase, setInitialPhase] = useState<InitialPhase>(entry ? 'pending' : 'none')
  const [initialError, setInitialError] = useState<unknown>(null)

  const translate = useTranslateStrategy()

  const applyTranslation = (response: TranslateStrategyResponse, isInitial: boolean) => {
    setDraft((current) => draftFromDsl(response.strategy, { name: current.name, description: current.description }))
    const { changes, assumptions, warnings } = response.meta
    const text = isInitial
      ? '입력한 목표로 초안을 만들었습니다. 블록에서 값을 직접 조정할 수 있습니다.'
      : changes.length > 0
        ? '요청대로 블록을 바꿨습니다.'
        : '바꿀 것이 없어 지금 전략을 그대로 두었습니다.'
    setConversation((current) => appendAssistantMessage(current, text, { changes, assumptions, warnings }))
    if (isInitial) {
      setInitialPhase('done')
    }
  }

  const failTranslation = (error: unknown, isInitial: boolean) => {
    setConversation((current) => appendAssistantMessage(current, describeTranslateError(error)))
    if (isInitial) {
      setInitialPhase('failed')
      setInitialError(error)
    }
  }

  /* 조건이 없는 전략은 백엔드가 빈 조건 그룹을 거부하므로 현재 전략을 보내지 않고 새로 만들게 한다. */
  const requestTranslation = (userInput: string, current: StrategyDraft, isInitial: boolean) => {
    translate.mutate(
      { userInput, currentStrategy: current.conditions.length > 0 ? dslFromDraft(current) : undefined },
      {
        onSuccess: (response) => applyTranslation(response, isInitial),
        onError: (error) => failTranslation(error, isInitial),
      },
    )
  }

  /* 첫 변환은 마운트 때 한 번만. StrictMode 가 effect 를 두 번 돌려도 요청은 한 번이어야 한다. */
  const initialRequested = useRef(false)
  useEffect(() => {
    if (!entry || initialRequested.current) {
      return
    }
    initialRequested.current = true
    requestTranslation(entry.prompt, createEmptyDraft(), true)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 진입 시 한 번만 실행한다
  }, [])

  const handleAsk = (text: string) => {
    setConversation((current) => setPending(appendUserMessage(current, text), true))
    requestTranslation(text, draft, false)
  }

  const retryInitial = () => {
    if (!entry) {
      return
    }
    setInitialPhase('pending')
    setInitialError(null)
    setConversation((current) => setPending(current, true))
    requestTranslation(entry.prompt, createEmptyDraft(), true)
  }

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

      {initialPhase === 'pending' && <LoadingState label="입력한 목표를 전략 블록으로 바꾸는 중…" />}
      {initialPhase === 'failed' && (
        <div className="callout crit" role="alert" style={{ marginBottom: 20 }}>
          <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 8v5M12 16h.01" />
          </svg>
          <span>{describeTranslateError(initialError)}</span>
          <button className="btn sm" type="button" onClick={retryInitial} style={{ marginLeft: 'auto' }}>
            다시 시도
          </button>
        </div>
      )}

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

      <AssistantPanel
        conversation={conversation}
        opening={describeStrategy(draft)}
        suggestions={suggestedRequests}
        onSend={handleAsk}
      />
    </section>
  )
}
