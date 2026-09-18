import assert from 'node:assert/strict'
import { test } from 'node:test'
import { totalEquity, totalPositionValue, valuePositions } from '../src/simulation/positions.ts'
import { createStartForm, setInitialCapital, setStrategyId, toCreateRequest, validateStartForm } from '../src/simulation/startForm.ts'

test('시작 조건은 초기 모의자산 기본값을 쓰고 편집 함수는 인수를 바꾸지 않는다', () => {
  const form = createStartForm('42')
  const snapshot = JSON.stringify(form)
  assert.equal(form.initialCapital, 3_000_000)
  setStrategyId(form, '7')
  setInitialCapital(form, 1)
  assert.equal(JSON.stringify(form), snapshot)
})

test('검증은 전략과 초기 모의자산을 잡는다', () => {
  const fields = (issues) => issues.map((issue) => issue.field)
  assert.deepEqual(fields(validateStartForm(createStartForm('42'))), [])
  assert.deepEqual(fields(validateStartForm(createStartForm(''))), ['strategyId'])
  assert.deepEqual(fields(validateStartForm(setInitialCapital(createStartForm('42'), 0))), ['initialCapital'])
  assert.deepEqual(fields(validateStartForm(setInitialCapital(createStartForm(''), Number.NaN))), ['strategyId', 'initialCapital'])
})

test('요청은 백테스트와 같은 서비스 고정값을 싣고 기간은 없다', () => {
  const request = toCreateRequest(setInitialCapital(createStartForm('42'), 5_000_000))
  assert.equal(request.strategyId, 42)
  assert.equal(request.initialCapital, 5_000_000)
  assert.equal(request.executionPriceRule, 'NEXT_CANDLE_OPEN')
  assert.equal(request.feeRate, 0.0005)
  assert.equal(request.slippageBps, 5)
  assert.equal(typeof request.engineVersion, 'string')
  assert.equal('periodStart' in request, false)
})

const position = (assetId, quantity, averageEntryPrice) => ({ assetId, quantity, averageEntryPrice, updatedAt: new Date(0) })

test('현재가가 있는 종목만 평가하고 없는 종목은 값을 비운다', () => {
  const valuations = valuePositions(
    [position('a', 2, 100), position('b', 1, 50)],
    new Map([['a', 120]]),
  )
  assert.equal(valuations[0].value, 240)
  assert.ok(Math.abs(valuations[0].pnlRate - 0.2) < 1e-12)
  assert.equal(valuations[1].price, null)
  assert.equal(valuations[1].value, null)
  assert.equal(valuations[1].pnlRate, null)
})

test('합계는 한 종목이라도 비면 비우고 모두 있으면 현금을 더한다', () => {
  const some = valuePositions([position('a', 2, 100), position('b', 1, 50)], new Map([['a', 120]]))
  assert.equal(totalPositionValue(some), null)
  assert.equal(totalEquity(1_000, some), null)
  const all = valuePositions([position('a', 2, 100), position('b', 1, 50)], new Map([['a', 120], ['b', 40]]))
  assert.equal(totalPositionValue(all), 280)
  assert.equal(totalEquity(1_000, all), 1_280)
  assert.equal(totalEquity(1_000, []), 1_000)
})

test('평균 매수가가 0 이면 수익률을 계산하지 않는다', () => {
  const [valuation] = valuePositions([position('a', 1, 0)], new Map([['a', 10]]))
  assert.equal(valuation.value, 10)
  assert.equal(valuation.pnlRate, null)
})
