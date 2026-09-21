import { useEffect, useRef, useState } from 'react'
import { useBlocker, useNavigate } from 'react-router'

import { routes, to } from '../../app/routes'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { LoadingState } from '../../components/LoadingState'
import { AssistantPanel } from '../assistant/AssistantPanel'
import {
  appendAssistantMessage,
  appendUserMessage,
  createConversation,
  describeStrategy,
  setPending,
  suggestedRequests,
  translationReplyText,
} from '../assistant/conversation'
import type { BuilderEntryState } from '../builderEntry'
import {
  addCondition,
  changedSections,
  createEmptyDraft,
  draftFromDsl,
  dslFromDraft,
  removeCondition,
  setName,
  setRanking,
  setRebalance,
  setTimeframe,
  setTopN,
  updateCondition,
  validateDraft,
  type BuilderSection,
  type StrategyDraft,
} from '../draft'
import { useCreateStrategy, useReviseStrategy, useTranslateStrategy } from '../queries'
import { describeSaveError } from '../saveErrorMessage'
import { describeTranslateError } from '../translateErrorMessage'
import { strategyNameMaxLength, type TranslateStrategyResponse } from '../types'
import { FilterBlock } from './FilterBlock'
import { RankingBlock } from './RankingBlock'
import { RebalanceBlock } from './RebalanceBlock'
import { StrategySummaryCard } from './StrategySummaryCard'
import { TopNBlock } from './TopNBlock'

/* 강조가 켜져 있는 시간. extensions.css 의 애니메이션보다 조금 길게 둔다. */
const highlightDurationMs = 1800

/* 홈에서 넘어온 첫 변환의 진행 상태. 성공하면 done, 실패하면 화면 위쪽에 안내와 재시도를 보인다. */
type InitialPhase = 'none' | 'pending' | 'failed' | 'done'

type StrategyEditorProps = {
  /* 새 전략은 빈 초안, 수정은 저장된 전략에서 만든 초안이다. 바뀌면 부모가 key 로 다시 마운트한다. */
  initialDraft: StrategyDraft
  entry: BuilderEntryState | null
  mode: 'new' | 'edit'
  /* 수정 모드의 바탕 판. 저장하면 이 판과 같은 뿌리의 다음 판이 만들어진다. */
  base?: { id: string; version: number }
}

/*
 * 전략 빌더 본체(목업 19·27). 편집 상태는 draft.ts 의 순수 함수로만 바꾼다. 홈에서 넘어온 목표
 * 문장은 진입 즉시 변환해 블록을 채우고, 전략봇의 요청은 지금 블록을 함께 보내 고쳐 받는다.
 * 저장이 실패하면 블록 위에 안내를 보이되 편집 내용은 그대로 둔다. 전략 행은 바뀌지 않으므로 수정 모드의
 * 저장은 같은 전략의 다음 판을 만든다. base.css 가 #view-builder 로 블록 격자를 정하므로 목업의 id 를 그대로 둔다.
 */
export function StrategyEditor({ initialDraft, entry, mode, base }: StrategyEditorProps) {
  const navigate = useNavigate()

  const [draft, setDraft] = useState(() => initialDraft)
  const [showIssues, setShowIssues] = useState(false)

  /*
   * 전략봇이 바꾼 영역을 잠깐 강조한다. 전략봇은 오른쪽 아래 패널이고 블록은 그 뒤라,
   * 표시가 없으면 무엇이 바뀌었는지 시야 밖에서 일어난다.
   */
  const [highlighted, setHighlighted] = useState<BuilderSection[]>([])
  const highlightTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const highlightSections = (sections: BuilderSection[]) => {
    if (highlightTimer.current !== null) {
      clearTimeout(highlightTimer.current)
    }
    setHighlighted(sections)
    if (sections.length === 0) {
      return
    }
    highlightTimer.current = setTimeout(() => setHighlighted([]), highlightDurationMs)
  }

  useEffect(
    () => () => {
      if (highlightTimer.current !== null) {
        clearTimeout(highlightTimer.current)
      }
    },
    [],
  )
  const issues = validateDraft(draft)
  const nameInputRef = useRef<HTMLInputElement>(null)

  /* 홈에서 온 문장은 첫 사용자 메시지로 두고 대기 상태로 시작한다. 변환 호출은 아래 effect 가 한다. */
  const [conversation, setConversation] = useState(() =>
    entry ? setPending(appendUserMessage(createConversation(), entry.prompt), true) : createConversation(),
  )
  const [initialPhase, setInitialPhase] = useState<InitialPhase>(entry ? 'pending' : 'none')
  const [initialError, setInitialError] = useState<unknown>(null)

  const translate = useTranslateStrategy()
  const create = useCreateStrategy()
  const revise = useReviseStrategy()
  const saving = create.isPending || revise.isPending
  const [saveError, setSaveError] = useState<unknown>(null)

  /*
   * 저장하지 않은 변경이 있으면 화면을 떠나기 전에 묻는다. 전략봇 초안을 받고 블록을 손보는 데 든 시간이
   * 오클릭 한 번에 사라지면 안 된다. 저장이 끝나 상세로 가는 이동은 막지 않는다.
   */
  const savedRef = useRef(false)
  const dirty = draft.name !== initialDraft.name || changedSections(initialDraft, draft).length > 0
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => dirty && !savedRef.current && currentLocation.pathname !== nextLocation.pathname,
  )
  useEffect(() => {
    if (!dirty) {
      return
    }
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  /*
   * strategy 가 null 이면 전략 설명이 아닌 입력이라 서버가 LLM 을 부르지 않은 것이다. 200 이고
   * 실패가 아니므로 초안을 건드리지 않고 사유만 전략봇에 띄운다. 사용자가 이미 채운 값이 있으면
   * 그대로 둬야 하고, initialPhase 를 failed 로 두면 오류 배너와 재시도가 뜬다.
   */
  const applyTranslation = (response: TranslateStrategyResponse, isInitial: boolean) => {
    const { strategy } = response
    const { changes, assumptions, warnings } = response.meta
    if (strategy !== null) {
      /*
       * 비교는 이름·설명을 보지 않으므로 base 없이 만든 초안이면 충분하다.
       * ponytail: 비교 기준이 렌더 시점 초안이라, 요청이 도는 사이에 블록을 고치면 강조가
       * 한 칸 넓어질 수 있다. 초안 적용 자체는 최신 값을 쓰므로 데이터는 잃지 않는다.
       */
      highlightSections(changedSections(draft, draftFromDsl(strategy)))
      setDraft((current) => draftFromDsl(strategy, { name: current.name, description: current.description }))
    }
    setConversation((current) =>
      appendAssistantMessage(current, translationReplyText(strategy, changes, isInitial), {
        changes,
        assumptions,
        warnings,
      }),
    )
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

  /* 화면 검증을 먼저 통과해야 서버로 보낸다. 성공하면 상세로 가고, 실패하면 편집 내용은 그대로 둔다. */
  const handleSave = () => {
    setShowIssues(true)
    if (issues.length > 0) {
      if (issues.some((issue) => issue.field === 'name')) {
        nameInputRef.current?.focus()
      }
      return
    }
    setSaveError(null)
    const body = {
      name: draft.name.trim(),
      description: draft.description.trim() || undefined,
      strategyDsl: dslFromDraft(draft),
    }
    const handlers = {
      onSuccess: (response: { strategyId: number }) => {
        savedRef.current = true
        navigate(to.strategyDetail(String(response.strategyId)))
      },
      onError: (error: unknown) => setSaveError(error),
    }
    /* 수정은 바탕 판과 같은 뿌리의 다음 판이고, 새 전략은 뿌리 1판이다. */
    if (base !== undefined) {
      revise.mutate({ strategyId: base.id, body }, handlers)
    } else {
      create.mutate(body, handlers)
    }
  }

  /* 처리 중에도 라벨을 바꾸지 않는다. 라벨이 바뀌면 버튼 폭이 달라져 옆 취소 버튼이 밀린다. 스피너만 붙인다. */
  const saveLabel = mode === 'edit' ? '다음 판으로 저장' : '전략 저장'
  const saveSpinner = saving ? <span className="spin" aria-hidden="true" /> : null
  const saveErrorView = saveError ? describeSaveError(saveError) : null

  return (
    <section className="view active" id="view-builder">
      <div className="page-head">
        <div>
          <span className="eyebrow">
            {mode === 'edit'
              ? `전략 수정 · v${base?.version ?? 1} 바탕 · 저장하면 다음 판이 만들어지고 이전 판의 결과는 그대로 남습니다`
              : '새 전략'}
          </span>
          <label htmlFor="strategyName" className="sr-only">
            전략 이름
          </label>
          <input
            ref={nameInputRef}
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
          <button className="btn primary sm" type="button" onClick={handleSave} disabled={saving} aria-busy={saving}>
            {saveSpinner}
            {saveLabel}
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

      {/* 저장 실패는 블록 위에 띄우고 편집 영역은 그대로 둔다. 문구가 "편집한 내용은 남아 있다" 고 말하는데 화면에서 사라지면 안 된다. */}
      {saveErrorView !== null && (
        <div className="callout crit save-error" role="alert">
          <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 8v5M12 16h.01" />
          </svg>
          <span>
            <b>{saveErrorView.title}</b> {saveErrorView.description}
            {saveErrorView.details.length > 0 && (
              <ul>
                {saveErrorView.details.map((row) => (
                  <li key={row.label}>
                    <b>{row.label}</b> {row.value}
                  </li>
                ))}
              </ul>
            )}
          </span>
          <button className="btn sm" type="button" onClick={handleSave} disabled={saving}>
            다시 저장
          </button>
        </div>
      )}

      <div className="edit-shell">
        <div>
          <div className="tabp on">
            <FilterBlock
              changed={highlighted.includes('filters')}
              conditions={draft.conditions}
              logic={draft.logic}
              onAdd={() => setDraft(addCondition(draft))}
              onChange={(id, patch) => setDraft(updateCondition(draft, id, patch))}
              onRemove={(id) => setDraft(removeCondition(draft, id))}
            />
            <RankingBlock
              changed={highlighted.includes('ranking')}
              ranking={draft.ranking}
              timeframe={draft.timeframe}
              onRankingChange={(patch) => setDraft(setRanking(draft, patch))}
              onTimeframeChange={(timeframe) => setDraft(setTimeframe(draft, timeframe))}
            />
            <TopNBlock changed={highlighted.includes('summary')} topN={draft.topN} onChange={(topN) => setDraft(setTopN(draft, topN))} />
            <RebalanceBlock
              changed={highlighted.includes('rebalance')}
              rebalance={draft.rebalance}
              onChange={(rebalance) => setDraft(setRebalance(draft, rebalance))}
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 }}>
            <button className="btn ghost" type="button" onClick={() => navigate(routes.strategies)}>
              취소
            </button>
            <button className="btn primary" type="button" onClick={handleSave} disabled={saving} aria-busy={saving}>
              {saveSpinner}
              {saveLabel}
            </button>
          </div>
        </div>

        <aside className="edit-side">
          <StrategySummaryCard
            changed={highlighted.includes('summary')}
            draft={draft}
            goal={entry?.prompt ?? null}
            issues={showIssues ? issues : []}
          />
        </aside>
      </div>

      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title="편집한 내용을 버릴까요?"
        description="저장하지 않은 변경이 있습니다. 나가면 지금까지 고친 블록과 이름이 사라집니다."
        confirmLabel="나가기"
        cancelLabel="계속 편집"
        danger
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      />

      <AssistantPanel
        conversation={conversation}
        opening={describeStrategy(draft)}
        suggestions={suggestedRequests(draft)}
        onSend={handleAsk}
      />
    </section>
  )
}
