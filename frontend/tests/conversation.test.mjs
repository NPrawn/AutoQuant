import assert from 'node:assert/strict'
import { test } from 'node:test'
import { appendAssistantMessage, createConversation, suggestedRequests, translationReplyText } from '../src/strategy/assistant/conversation.ts'

const dsl = {
  timeframe: '1D',
  filters: { type: 'GROUP', logic: 'AND', children: [] },
  ranking: { factor: 'RETURN', period: 20, direction: 'DESC' },
  selection: { topN: 5 },
  rebalance: 'WEEKLY',
}

test('전략이 없으면 초안을 바꾸라고 말하지 않고 무엇을 알려 달라고 한다', () => {
  const text = translationReplyText(null, [], false)
  assert.match(text, /알려 주세요/)
  assert.doesNotMatch(text, /바꿨습니다|초안을 만들었습니다/)
})

test('진입 변환에서 전략이 없어도 실패로 말하지 않는다', () => {
  assert.equal(translationReplyText(null, [], true), translationReplyText(null, [], false))
})

test('진입 변환이 성공하면 초안을 만들었다고 말한다', () => {
  assert.match(translationReplyText(dsl, [], true), /초안을 만들었습니다/)
})

test('편집에서 바뀐 것이 있으면 바꿨다고, 없으면 그대로 뒀다고 말한다', () => {
  assert.match(translationReplyText(dsl, ['topN 값이 바뀌었습니다.'], false), /바꿨습니다/)
  assert.match(translationReplyText(dsl, [], false), /그대로 두었습니다/)
})

test('전략이 없어도 사유는 말풍선에 그대로 실린다', () => {
  const assumptions = ['입력이 짧아 전략을 만들지 않았습니다.']
  const conversation = appendAssistantMessage(createConversation(), translationReplyText(null, [], true), {
    assumptions,
    changes: [],
    warnings: [],
  })
  assert.deepEqual(conversation.messages.at(-1).notes.assumptions, assumptions)
  assert.equal(conversation.pending, false)
})

test('이미 걸린 조건을 또 걸라거나 하나뿐인 조건을 지우라는 칩은 내놓지 않는다', () => {
  const oneVolumeCondition = {
    name: '',
    description: '',
    timeframe: '1D',
    logic: 'AND',
    conditions: [{ id: 'c1', kind: 'simple', factor: 'VOLUME_RATIO', period: 20, operator: 'GTE', value: 1.5 }],
    ranking: { factor: 'VOLATILITY', period: 20, direction: 'DESC' },
    topN: 3,
    rebalance: 'WEEKLY',
  }
  const chips = suggestedRequests(oneVolumeCondition)

  assert.ok(!chips.includes('거래량 급증 조건을 추가해줘'))
  assert.ok(!chips.includes('거래량 조건은 빼줘'))
  assert.ok(!chips.includes('순위 기준을 변동성으로 바꿔줘'))
  assert.ok(chips.length > 0)
})

test('조건이 둘이면 거래량 조건 제거를 내놓는다', () => {
  const twoConditions = {
    name: '',
    description: '',
    timeframe: '4H',
    logic: 'AND',
    conditions: [
      { id: 'c1', kind: 'simple', factor: 'RSI', period: 14, operator: 'LT', value: 35 },
      { id: 'c2', kind: 'simple', factor: 'VOLUME_RATIO', period: 20, operator: 'GTE', value: 1.8 },
    ],
    ranking: { factor: 'RETURN', period: 20, direction: 'DESC' },
    topN: 5,
    rebalance: 'WEEKLY',
  }

  assert.ok(suggestedRequests(twoConditions).includes('거래량 조건은 빼줘'))
})
