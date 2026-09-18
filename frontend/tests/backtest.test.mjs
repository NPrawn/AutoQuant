import assert from 'node:assert/strict'
import { test } from 'node:test'
import { formatPriceKrw, isRunInProgress } from '../src/backtest/labels.ts'
import { periodBounds } from '../src/backtest/periodBounds.ts'
import {
  applyPreset, clampPeriodStart, createRunForm, detectPreset, setInitialCapital,
  setPeriodEnd, setPeriodStart, setStrategyId, toCreateRequest, toDateInput, validateRunForm,
} from '../src/backtest/runForm.ts'

const today = new Date('2026-09-14T05:00:00Z')
const coverage = { start: '2025-07-20', end: '2026-09-13' }

/* 운영 전략과 같은 모양. 랭킹 20기간이라 워밍업 21봉, 일봉이라 하한이 21일 밀린다. */
const dsl = (rankingPeriod = 20) => ({
  timeframe: '1D',
  filters: {
    type: 'GROUP',
    logic: 'AND',
    children: [
      { type: 'CONDITION', left: { type: 'FACTOR', factor: 'RSI', period: 14 }, operator: 'LT', right: { type: 'VALUE', value: 70 } },
    ],
  },
  ranking: { factor: 'RETURN', period: rankingPeriod, direction: 'DESC' },
  selection: { topN: 5 },
  rebalance: { period: 'WEEKLY' },
})

const bounds = periodBounds(coverage, dsl(), today)
const form = (b = bounds) => createRunForm('42', today, b)

test('기본 기간은 워밍업을 반영한 하한부터 오늘까지고 바로 실행할 수 있다', () => {
  const created = form()
  assert.equal(created.periodStart, '2025-08-10')
  assert.equal(created.periodEnd, toDateInput(today))
  assert.deepEqual(validateRunForm(created, today, bounds), [])
})

test('보유 시작일을 그대로 쓰면 워밍업이 없어 검증에 걸린다', () => {
  const issues = validateRunForm(setPeriodStart(form(), coverage.start), today, bounds)
  assert.deepEqual(issues.map((issue) => issue.field), ['periodStart'])
  assert.match(issues[0].message, /21개/)
  assert.match(issues[0].message, /2025-08-10/)
})

test('프리셋은 시작일만 옮기고 하한 아래로 내려가지 않는다', () => {
  assert.equal(applyPreset(form(), '1m', today, bounds).periodStart, '2026-08-14')
  assert.equal(applyPreset(form(), '3m', today, bounds).periodStart, '2026-06-14')
  const late = periodBounds({ start: '2026-08-20', end: '2026-09-13' }, dsl(1), today)
  assert.equal(applyPreset(form(late), '3m', today, late).periodStart, late.earliestStart)
  assert.equal(applyPreset(form(late), 'all', today, late).periodStart, late.earliestStart)
})

test('직접 고친 기간은 어느 프리셋도 켜지 않는다', () => {
  assert.equal(detectPreset(form(), today, bounds), 'all')
  assert.equal(detectPreset(applyPreset(form(), '1m', today, bounds), today, bounds), '1m')
  assert.equal(detectPreset(setPeriodStart(form(), '2026-07-01'), today, bounds), null)
})

test('전략을 바꿔 워밍업이 늘면 시작일만 하한으로 끌어올린다', () => {
  const longer = periodBounds(coverage, dsl(60), today)
  assert.equal(longer.earliestStart, '2025-09-19')
  const clamped = clampPeriodStart(form(), longer)
  assert.equal(clamped.periodStart, '2025-09-19')
  assert.equal(clamped.periodEnd, form().periodEnd)
  /* 하한보다 뒤라면 손대지 않고 같은 객체를 돌려준다. */
  const later = setPeriodStart(form(), '2026-01-01')
  assert.equal(clampPeriodStart(later, longer), later)
})

test('편집 함수는 인수를 바꾸지 않는다', () => {
  const original = form()
  const snapshot = JSON.stringify(original)
  setStrategyId(original, '7')
  setPeriodEnd(original, '2020-01-01')
  setInitialCapital(original, 1)
  clampPeriodStart(original, periodBounds(coverage, dsl(60), today))
  assert.equal(JSON.stringify(original), snapshot)
})

test('검증은 전략, 기간 순서, 미래 종료일, 초기자산을 잡는다', () => {
  const fields = (issues) => issues.map((issue) => issue.field)
  assert.deepEqual(fields(validateRunForm(setStrategyId(form(), ''), today, bounds)), ['strategyId'])
  assert.deepEqual(fields(validateRunForm(setPeriodStart(form(), '2027-01-01'), today, bounds)), ['periodEnd'])
  assert.deepEqual(fields(validateRunForm(setPeriodEnd(form(), '2030-01-01'), today, bounds)), ['periodEnd'])
  assert.deepEqual(fields(validateRunForm(setInitialCapital(form(), 0), today, bounds)), ['initialCapital'])
  assert.deepEqual(fields(validateRunForm(setInitialCapital(form(), Number.NaN), today, bounds)), ['initialCapital'])
})

test('요청은 고른 날짜의 로컬 하루 경계를 보내 표시할 때 날짜가 밀리지 않는다', () => {
  const request = toCreateRequest(setPeriodEnd(setPeriodStart(form(), '2026-06-01'), '2026-08-31'))
  assert.equal(request.strategyId, 42)
  assert.equal(toDateInput(new Date(request.periodStart)), '2026-06-01')
  assert.equal(toDateInput(new Date(request.periodEnd)), '2026-08-31')
  assert.equal(new Date(request.periodEnd).getTime() - new Date(request.periodStart).getTime(), 92 * 86400000 - 1)
  assert.equal(request.quoteCurrency, 'KRW')
  assert.equal(request.executionPriceRule, 'NEXT_CANDLE_OPEN')
})

test('조건 폼은 대기·계산 중인 실행에만 잠기고 끝난 실행에는 풀린다', () => {
  assert.equal(isRunInProgress('QUEUED'), true)
  assert.equal(isRunInProgress('RUNNING'), true)
  assert.equal(isRunInProgress('COMPLETED'), false)
  assert.equal(isRunInProgress('FAILED'), false)
})

test('코인 가격은 금액이 작을수록 소수를 더 남겨 저가 코인이 0 으로 찍히지 않는다', () => {
  assert.equal(formatPriceKrw(98_500_000), '98,500,000')
  assert.equal(formatPriceKrw(1234.5), '1,235')
  assert.equal(formatPriceKrw(523.4), '523.4')
  assert.equal(formatPriceKrw(45.678), '45.68')
  assert.equal(formatPriceKrw(2.3456), '2.346')
  assert.equal(formatPriceKrw(0.4523), '0.4523')
  assert.equal(formatPriceKrw(null), '—')
})
