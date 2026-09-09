import { conditionLimit, type StrategyDraft } from '../draft.ts'
import { describeCondition, directionLabels, factorMeta, logicLabels, rebalanceMeta } from '../labels.ts'
import type { StrategyDsl } from '../types'

/*
 * 전략봇 대화 상태. 화면은 이 함수들로만 상태를 바꾸고, 변환 API 응답을 대화에 넣는 일은
 * 연동 작업이 appendAssistantMessage 로 한다. 메시지에 id 를 두는 이유는 조건과 같다.
 */
export type AssistantNotes = {
  assumptions: string[]
  changes: string[]
  warnings: string[]
}

export type AssistantMessage =
  | { id: string; role: 'user'; text: string }
  | { id: string; role: 'assistant'; text: string; notes?: AssistantNotes }

export type Conversation = {
  messages: AssistantMessage[]
  pending: boolean
}

const newId = (): string => crypto.randomUUID()

export function createConversation(): Conversation {
  return { messages: [], pending: false }
}

export function appendUserMessage(conversation: Conversation, text: string): Conversation {
  return {
    ...conversation,
    messages: [...conversation.messages, { id: newId(), role: 'user', text }],
  }
}

export function appendAssistantMessage(conversation: Conversation, text: string, notes?: AssistantNotes): Conversation {
  const hasNotes = notes && (notes.assumptions.length > 0 || notes.changes.length > 0 || notes.warnings.length > 0)
  return {
    ...conversation,
    pending: false,
    messages: [...conversation.messages, { id: newId(), role: 'assistant', text, ...(hasNotes ? { notes } : {}) }],
  }
}

export function setPending(conversation: Conversation, pending: boolean): Conversation {
  return { ...conversation, pending }
}

/* 목업의 첫 말풍선. 지금 블록에 놓인 전략을 한 문장으로 읽어 준다. 블록이 바뀌면 다시 계산한다. */
export function describeStrategy(draft: StrategyDraft): string {
  const simple = draft.conditions.filter((condition) => condition.kind === 'simple')
  const ranking = `${draft.ranking.period}기간 ${factorMeta[draft.ranking.factor].short}이 ${directionLabels[draft.ranking.direction]}인 ${draft.topN}개`
  const cadence = `${rebalanceMeta[draft.rebalance].label} 다시 뽑습니다.`

  if (simple.length === 0) {
    return `지금 전략은 후보 조건 없이 전체 종목에서 ${ranking}를 ${cadence}`
  }
  const filters = simple
    .map((condition) => describeCondition(condition.factor, condition.operator, condition.value))
    .join(` ${logicLabels[draft.logic]} `)
  return `지금 전략은 ${filters} 인 코인만 후보로 남기고, 그중 ${ranking}를 ${cadence}`
}

/*
 * 변환 응답에 붙일 전략봇 문장. strategy 가 null 인 것은 실패가 아니라 전략 설명이 아닌
 * 입력이라 서버가 LLM 을 부르지 않은 것이다. 왜 안 만들었는지는 meta.assumptions 가 말하므로
 * 여기서는 다음에 무엇을 하면 되는지만 말한다.
 */
export function translationReplyText(strategy: StrategyDsl | null, changes: string[], isInitial: boolean): string {
  if (strategy === null) {
    return '전략으로 옮길 내용을 찾지 못했습니다. 어떤 코인을 어떤 기준으로 고르고 싶은지 알려 주세요.'
  }
  if (isInitial) {
    return '입력한 목표로 초안을 만들었습니다. 블록에서 값을 직접 조정할 수 있습니다.'
  }
  return changes.length > 0 ? '요청대로 블록을 바꿨습니다.' : '블록은 그대로 두었습니다. 왜 그런지는 아래에 적었습니다.'
}

/*
 * 제안 문장. 변환 API 는 질문에 답하는 게 아니라 전략을 바꾸는 요청을 받으므로 목업의
 * 질문형 칩 대신 블록을 바꾸는 요청형으로 둔다.
 *
 * 후보는 모두 ai/tests/fixtures/edit_cases.json 에서 통과가 측정된 편집이고 갈래가 서로
 * 다르다 — 조건 추가·조건 제거·랭킹·리밸런싱·시간프레임·종목 수. 칩은 이 봇이 무엇을 할
 * 수 있는지 알려 주는 유일한 장치라, 못 하는 것을 넣으면 첫 시도가 실패하고 그 뒤로 쓰지
 * 않는다.
 *
 * 그래서 지금 전략에서 실제로 뭔가 바뀌는 것만 고른다. 이미 걸린 조건을 또 걸라는 칩과
 * 하나뿐인 조건을 지우라는 칩은 눌러도 블록이 그대로라, 봇이 고장 난 것처럼 보인다.
 * 조건 제거가 하나 남았을 때 막히는 것은 DSL 이 조건 없는 전략을 표현하지 못해서다.
 */
const chipLimit = 4

export function suggestedRequests(draft: StrategyDraft): string[] {
  const simple = draft.conditions.filter((condition) => condition.kind === 'simple')
  const hasVolume = simple.some((condition) => condition.factor === 'VOLUME_RATIO')
  const candidates: [boolean, string][] = [
    [!hasVolume && draft.conditions.length < conditionLimit, '거래량 급증 조건을 추가해줘'],
    [hasVolume && draft.conditions.length > 1, '거래량 조건은 빼줘'],
    [draft.ranking.factor !== 'VOLATILITY', '순위 기준을 변동성으로 바꿔줘'],
    [draft.rebalance !== 'MONTHLY', '재선정을 매월로 바꿔줘'],
    [draft.timeframe !== '1H', '1시간봉으로 바꿔줘'],
    [draft.topN !== 10, '10개 담게 해줘'],
  ]
  return candidates
    .filter(([applies]) => applies)
    .slice(0, chipLimit)
    .map(([, request]) => request)
}
