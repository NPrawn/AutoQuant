import assert from 'node:assert/strict'
import { test } from 'node:test'
import { changedSections, draftFromDsl, topNOptions } from '../src/strategy/draft.ts'

const dsl = (overrides = {}) => ({
  timeframe: '1D',
  filters: {
    type: 'GROUP',
    logic: 'AND',
    children: [
      {
        type: 'CONDITION',
        left: { type: 'FACTOR', factor: 'RSI', period: 14 },
        operator: 'LT',
        right: { type: 'VALUE', value: 30 },
      },
    ],
  },
  ranking: { factor: 'RETURN', period: 20, direction: 'DESC' },
  selection: { topN: 5 },
  rebalance: { period: 'WEEKLY' },
  ...overrides,
})

test('같은 DSL 에서 만든 두 초안은 조건 id 가 달라도 바뀐 것이 없다', () => {
  const before = draftFromDsl(dsl())
  const after = draftFromDsl(dsl())
  assert.notEqual(before.conditions[0].id, after.conditions[0].id)
  assert.deepEqual(changedSections(before, after), [])
})

test('조건이 바뀌면 필터 블록만 짚는다', () => {
  const after = dsl()
  after.filters.children[0].right.value = 25
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(after)), ['filters'])
})

test('조건이 늘거나 줄어도 필터 블록을 짚는다', () => {
  const after = dsl()
  after.filters.children = []
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(after)), ['filters'])
})

test('순위 기준과 봉 주기는 랭킹 블록으로 묶인다', () => {
  const ranking = dsl({ ranking: { factor: 'VOLATILITY', period: 20, direction: 'DESC' } })
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(ranking)), ['ranking'])

  const timeframe = dsl({ timeframe: '4H' })
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(timeframe)), ['ranking'])
})

test('재선정 주기는 리밸런싱 블록이다', () => {
  const after = dsl({ rebalance: { period: 'MONTHLY' } })
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(after)), ['rebalance'])
})

test('투자 종목 수는 블록이 아니라 요약 카드에만 보이므로 summary 를 짚는다', () => {
  const after = dsl({ selection: { topN: 10 } })
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(after)), ['summary'])
})

test('여러 영역이 함께 바뀌면 모두 짚는다', () => {
  const after = dsl({ rebalance: { period: 'DAILY' }, selection: { topN: 3 } })
  after.filters.children[0].right.value = 40
  assert.deepEqual(changedSections(draftFromDsl(dsl()), draftFromDsl(after)), ['filters', 'rebalance', 'summary'])
})

test('이름과 설명은 전략의 내용이 아니라 비교하지 않는다', () => {
  const before = draftFromDsl(dsl(), { name: '옛 이름', description: '옛 설명' })
  const after = draftFromDsl(dsl(), { name: '새 이름', description: '새 설명' })
  assert.deepEqual(changedSections(before, after), [])
})

test('종목 수 선택지는 기본 셋에 현재 값을 더해 오름차순이고 잘못된 값은 뺀다', () => {
  assert.deepEqual(topNOptions(5), [3, 5, 10])
  assert.deepEqual(topNOptions(7), [3, 5, 7, 10])
  assert.deepEqual(topNOptions(0), [3, 5, 10])
})
