import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  coverageFromInstants, fallbackPeriodStart, periodBounds, shiftByCandles, toDateInput, warmupCandleCount,
} from '../src/backtest/periodBounds.ts'

const today = new Date('2026-09-14T05:00:00Z')

const dsl = (timeframe, rankingPeriod, filterPeriods) => ({
  timeframe,
  filters: {
    type: 'GROUP',
    logic: 'AND',
    children: filterPeriods.map((period) => ({
      type: 'CONDITION',
      left: { type: 'FACTOR', factor: 'RSI', period },
      operator: 'LT',
      right: { type: 'VALUE', value: 70 },
    })),
  },
  ranking: { factor: 'RETURN', period: rankingPeriod, direction: 'DESC' },
  selection: { topN: 5 },
  rebalance: { period: 'WEEKLY' },
})

test('보유 구간은 서버가 준 양 끝 시각을 날짜 표기로 옮긴 값이다', () => {
  assert.deepEqual(
    coverageFromInstants('2018-01-01T00:00:00Z', '2026-09-13T00:00:00Z'),
    { start: toDateInput(new Date('2018-01-01T00:00:00Z')), end: toDateInput(new Date('2026-09-13T00:00:00Z')) },
  )
})

test('서버가 보유 구간을 비우면 하한이 없다', () => {
  assert.equal(coverageFromInstants(null, null), null)
  assert.equal(coverageFromInstants('2018-01-01T00:00:00Z', null), null)
  assert.equal(coverageFromInstants(null, '2026-09-13T00:00:00Z'), null)
})

test('보유 구간 시각을 읽을 수 없으면 하한이 없다', () => {
  assert.equal(coverageFromInstants('not-a-date', '2026-09-13T00:00:00Z'), null)
})

test('워밍업 봉 수는 랭킹과 필터 중 가장 긴 기간에 하나를 더한 값이다', () => {
  assert.equal(warmupCandleCount(dsl('1D', 20, [14])), 21)
  assert.equal(warmupCandleCount(dsl('1D', 5, [60])), 61)
  assert.equal(warmupCandleCount(dsl('1D', 20, [])), 21)
})

test('중첩된 조건 그룹도 끝까지 훑는다', () => {
  const nested = dsl('1D', 5, [10])
  nested.filters.children.push({
    type: 'GROUP',
    logic: 'OR',
    children: [
      { type: 'CONDITION', left: { type: 'FACTOR', factor: 'VOLATILITY', period: 40 }, operator: 'LT', right: { type: 'VALUE', value: 8 } },
    ],
  })
  assert.equal(warmupCandleCount(nested), 41)
})

test('봉 개수를 날짜로 옮길 때 하루 미만은 하루로 올린다', () => {
  assert.equal(shiftByCandles('2025-07-20', 21, '1D'), '2025-08-10')
  assert.equal(shiftByCandles('2025-07-20', 21, '4H'), '2025-07-24')
  assert.equal(shiftByCandles('2025-07-20', 21, '1H'), '2025-07-21')
})

test('하한은 보유 시작일에 워밍업 구간을 더한 날이다', () => {
  const coverage = { start: '2025-07-20', end: '2026-09-13' }
  const bounds = periodBounds(coverage, dsl('1D', 20, [14]), today)
  assert.equal(bounds.earliestStart, '2025-08-10')
  assert.equal(bounds.warmupCandles, 21)
  assert.equal(bounds.tooShort, false)
})

test('전략이나 보유 구간을 모르면 워밍업 없이 보유 시작일이 하한이다', () => {
  const coverage = { start: '2025-07-20', end: '2026-09-13' }
  assert.equal(periodBounds(coverage, null, today).earliestStart, '2025-07-20')
  assert.equal(periodBounds(coverage, null, today).warmupCandles, 0)
  assert.equal(periodBounds(null, dsl('1D', 20, [14]), today).earliestStart, fallbackPeriodStart)
})

test('워밍업을 빼면 평가할 구간이 남지 않는 경우를 알린다', () => {
  const coverage = { start: '2026-09-01', end: '2026-09-13' }
  const bounds = periodBounds(coverage, dsl('1D', 20, [14]), today)
  assert.equal(bounds.earliestStart, '2026-09-22')
  assert.equal(bounds.tooShort, true)
  assert.ok(bounds.earliestStart > toDateInput(today))
})
