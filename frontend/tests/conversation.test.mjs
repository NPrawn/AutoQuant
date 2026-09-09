import assert from 'node:assert/strict'
import { test } from 'node:test'
import { appendAssistantMessage, createConversation, translationReplyText } from '../src/strategy/assistant/conversation.ts'

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
