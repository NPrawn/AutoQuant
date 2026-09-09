import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  applyPreset, createRunForm, detectPreset, earliestPeriodStart, setInitialCapital,
  setPeriodEnd, setPeriodStart, setStrategyId, toCreateRequest, toDateInput, validateRunForm,
} from '../src/backtest/runForm.ts'

const today = new Date('2026-09-09T05:00:00Z')
const form = () => createRunForm('42', today)

test('기본 조건은 전체 구간과 오늘까지를 쓴다', () => {
  const created = form()
  assert.equal(created.periodStart, earliestPeriodStart)
  assert.equal(created.periodEnd, toDateInput(today))
  assert.equal(validateRunForm(created, today).length, 0)
})

test('프리셋은 시작일만 옮기고 상장 이전으로 내려가지 않는다', () => {
  assert.equal(applyPreset(form(), '1y', today).periodStart, '2025-09-09')
  assert.equal(applyPreset(form(), '3y', today).periodStart, '2023-09-09')
  const early = new Date('2019-01-01T00:00:00Z')
  assert.equal(applyPreset(createRunForm('42', early), '3y', early).periodStart, earliestPeriodStart)
})

test('직접 고친 기간은 어느 프리셋도 켜지 않는다', () => {
  assert.equal(detectPreset(form(), today), 'all')
  assert.equal(detectPreset(applyPreset(form(), '1y', today), today), '1y')
  assert.equal(detectPreset(setPeriodStart(form(), '2020-05-05'), today), null)
})

test('편집 함수는 인수를 바꾸지 않는다', () => {
  const original = form()
  const snapshot = JSON.stringify(original)
  setStrategyId(original, '7')
  setPeriodEnd(original, '2020-01-01')
  setInitialCapital(original, 1)
  assert.equal(JSON.stringify(original), snapshot)
})

test('검증은 전략, 기간 순서, 미래 종료일, 초기자산을 잡는다', () => {
  const fields = (issues) => issues.map((issue) => issue.field)
  assert.deepEqual(fields(validateRunForm(setStrategyId(form(), ''), today)), ['strategyId'])
  assert.deepEqual(fields(validateRunForm(setPeriodStart(form(), '2027-01-01'), today)), ['periodEnd'])
  assert.deepEqual(fields(validateRunForm(setPeriodEnd(form(), '2030-01-01'), today)), ['periodEnd'])
  assert.deepEqual(fields(validateRunForm(setPeriodStart(form(), '2017-01-01'), today)), ['periodStart'])
  assert.deepEqual(fields(validateRunForm(setInitialCapital(form(), 0), today)), ['initialCapital'])
  assert.deepEqual(fields(validateRunForm(setInitialCapital(form(), Number.NaN), today)), ['initialCapital'])
})

test('요청은 고른 날짜의 로컬 하루 경계를 보내 표시할 때 날짜가 밀리지 않는다', () => {
  const request = toCreateRequest(setPeriodEnd(setPeriodStart(form(), '2024-01-01'), '2024-12-31'))
  assert.equal(request.strategyId, 42)
  assert.equal(toDateInput(new Date(request.periodStart)), '2024-01-01')
  assert.equal(toDateInput(new Date(request.periodEnd)), '2024-12-31')
  assert.equal(new Date(request.periodEnd).getTime() - new Date(request.periodStart).getTime(), 366 * 86400000 - 1)
  assert.equal(request.quoteCurrency, 'KRW')
  assert.equal(request.executionPriceRule, 'NEXT_CANDLE_OPEN')
})
