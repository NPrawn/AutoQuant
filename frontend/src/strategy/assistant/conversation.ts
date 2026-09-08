import type { StrategyDraft } from '../draft'
import { describeCondition, directionLabels, factorMeta, logicLabels, rebalanceMeta } from '../labels'

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

/* 목업 19 의 제안 질문. 사용자가 무엇을 물을 수 있는지 보여 준다. */
export const suggestedQuestions = [
  '통과 종목이 너무 많아요',
  '하락장에 강하게 만들려면?',
  'SMA 이격률이 뭐예요?',
  '재선정 주기는 어떻게 정해요?',
] as const
