import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  applyPreset, coverageFromOpenTimes, createRunForm, detectPreset, fallbackPeriodStart, periodFloor, setInitialCapital,
  setPeriodEnd, setPeriodStart, setStrategyId, toCreateRequest, toDateInput, validateRunForm,
} from '../src/backtest/runForm.ts'

const today = new Date('2026-09-09T05:00:00Z')
const coverage = { start: '2026-05-24', end: '2026-09-08' }
const form = (cov = coverage) => createRunForm('42', today, cov)

test('보유 구간은 일봉 시각의 첫·마지막 날이고 봉이 없으면 null 이다', () => {
  const day = (iso) => new Date(iso).getTime()
  assert.deepEqual(coverageFromOpenTimes([day('2026-06-01T00:00:00Z'), day('2026-05-24T00:00:00Z'), day('2026-09-08T00:00:00Z')]), coverage)
  assert.equal(coverageFromOpenTimes([]), null)
  assert.equal(periodFloor(coverage), '2026-05-24')
  assert.equal(periodFloor(null), fallbackPeriodStart)
})

test('기본 조건은 보유 시작일부터 오늘까지고 구간을 모르면 하한부터다', () => {
  const created = form()
  assert.equal(created.periodStart, '2026-05-24')
  assert.equal(created.periodEnd, toDateInput(today))
  assert.equal(validateRunForm(created, today, coverage).length, 0)
  assert.equal(form(null).periodStart, fallbackPeriodStart)
})

test('프리셋은 시작일만 옮기고 보유 시작일 아래로 내려가지 않는다', () => {
  assert.equal(applyPreset(form(), '1m', today, coverage).periodStart, '2026-08-09')
  assert.equal(applyPreset(form(), '3m', today, coverage).periodStart, '2026-06-09')
  const shortCoverage = { start: '2026-08-20', end: '2026-09-08' }
  assert.equal(applyPreset(form(shortCoverage), '3m', today, shortCoverage).periodStart, '2026-08-20')
  assert.equal(applyPreset(form(shortCoverage), 'all', today, shortCoverage).periodStart, '2026-08-20')
})

test('직접 고친 기간은 어느 프리셋도 켜지 않는다', () => {
  assert.equal(detectPreset(form(), today, coverage), 'all')
  assert.equal(detectPreset(applyPreset(form(), '1m', today, coverage), today, coverage), '1m')
  assert.equal(detectPreset(setPeriodStart(form(), '2026-07-01'), today, coverage), null)
})

test('편집 함수는 인수를 바꾸지 않는다', () => {
  const original = form()
  const snapshot = JSON.stringify(original)
  setStrategyId(original, '7')
  setPeriodEnd(original, '2020-01-01')
  setInitialCapital(original, 1)
  assert.equal(JSON.stringify(original), snapshot)
})

test('검증은 전략, 보유 시작일 이전, 기간 순서, 미래 종료일, 초기자산을 잡는다', () => {
  const fields = (issues) => issues.map((issue) => issue.field)
  assert.deepEqual(fields(validateRunForm(setStrategyId(form(), ''), today, coverage)), ['strategyId'])
  assert.deepEqual(fields(validateRunForm(setPeriodStart(form(), '2026-05-01'), today, coverage)), ['periodStart'])
  assert.match(validateRunForm(setPeriodStart(form(), '2026-05-01'), today, coverage)[0].message, /2026-05-24/)
  assert.deepEqual(fields(validateRunForm(setPeriodStart(form(), '2027-01-01'), today, coverage)), ['periodEnd'])
  assert.deepEqual(fields(validateRunForm(setPeriodEnd(form(), '2030-01-01'), today, coverage)), ['periodEnd'])
  assert.deepEqual(fields(validateRunForm(setPeriodStart(form(null), '2017-01-01'), today, null)), ['periodStart'])
  assert.deepEqual(fields(validateRunForm(setInitialCapital(form(), 0), today, coverage)), ['initialCapital'])
  assert.deepEqual(fields(validateRunForm(setInitialCapital(form(), Number.NaN), today, coverage)), ['initialCapital'])
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
