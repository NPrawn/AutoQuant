import assert from 'node:assert/strict'
import { test } from 'node:test'
import { summarizeDsl, summarizeFilters } from '../src/strategy/summary.ts'

const condition = (factor, operator, value, period = 14) => ({
  type: 'CONDITION',
  left: { type: 'FACTOR', factor, period },
  operator,
  right: { type: 'VALUE', value },
})

const dsl = (filters) => ({
  timeframe: '1D',
  filters,
  ranking: { factor: 'RETURN', period: 20, direction: 'DESC' },
  selection: { topN: 5 },
  rebalance: 'WEEKLY',
})

test('단순 조건은 문장으로, 편집 불가 조건은 개수로 요약한다', () => {
  const summary = summarizeFilters(
    dsl({
      type: 'GROUP',
      logic: 'OR',
      children: [
        condition('RSI', 'LT', 30),
        { type: 'CONDITION', left: { type: 'FACTOR', factor: 'RSI', period: 14 }, operator: 'GT', right: { type: 'FACTOR', factor: 'SMA_GAP', period: 20 } },
      ],
    }),
  )
  assert.equal(summary.logic, 'OR')
  assert.equal(summary.conditions.length, 1)
  assert.match(summary.conditions[0], /RSI/)
  assert.equal(summary.otherCount, 1)
})

test('목록 한 줄 요약은 조건과 순위 기준을 가운데점으로 잇고 조건이 없으면 그렇게 적는다', () => {
  const line = summarizeDsl(dsl({ type: 'GROUP', logic: 'AND', children: [condition('RSI', 'LT', 30), condition('VOLUME_RATIO', 'GT', 1.2)] }))
  assert.match(line, /AND/)
  assert.match(line, / · /)
  assert.match(summarizeDsl(dsl({ type: 'GROUP', logic: 'AND', children: [] })), /^조건 없음 · /)
  assert.match(summarizeDsl(dsl(condition('RSI', 'LT', 30))), /^RSI/)
})
