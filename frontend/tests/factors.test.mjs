import assert from 'node:assert/strict'
import { test } from 'node:test'
import { factorRows, factorTimeframe, missingFactorNote, tradeAmountRank } from '../src/market/factors.ts'

const response = (factors) => ({ assetId: 'a-1', timeframe: '1D', asOf: '2026-09-13T00:00:00Z', factors })

test('봉 간격을 전략이 쓰는 봉 주기로 옮긴다', () => {
  assert.equal(factorTimeframe(60), '1H')
  assert.equal(factorTimeframe(240), '4H')
  assert.equal(factorTimeframe(1440), '1D')
})

test('팩터를 계산할 수 없는 짧은 간격은 일봉으로 본다', () => {
  for (const interval of [1, 2, 5, 15]) {
    assert.equal(factorTimeframe(interval), '1D')
  }
})

test('서버가 보낸 기간과 값을 빌더와 같은 표기로 만든다', () => {
  const rows = factorRows(response([
    { factor: 'RETURN', period: 20, value: 4.1 },
    { factor: 'RSI', period: 14, value: 62.14 },
    { factor: 'VOLUME_RATIO', period: 20, value: 1.237 },
  ]))

  assert.deepEqual(rows.map((row) => row.label), ['수익률 (20)', 'RSI (14)', '거래량 비율 (20)'])
  assert.deepEqual(rows.map((row) => row.text), ['+4.1%', '62.1', '1.24배'])
})

test('부호가 방향을 뜻하는 팩터는 등락 색, 낙폭은 낙폭 색을 쓴다', () => {
  const rows = factorRows(response([
    { factor: 'RETURN', period: 20, value: 4.1 },
    { factor: 'DRAWDOWN', period: 60, value: -8.2 },
    { factor: 'RSI', period: 14, value: 62.1 },
    { factor: 'VOLATILITY', period: 20, value: 3.4 },
  ]))

  assert.deepEqual(rows.map((row) => row.tone), ['updown up', 'dd-val', '', ''])
})

test('값이 없는 팩터는 색 없이 자리만 남긴다', () => {
  const rows = factorRows(response([{ factor: 'RETURN', period: 20, value: null }]))

  assert.deepEqual(rows, [{ factor: 'RETURN', label: '수익률 (20)', text: '—', tone: '' }])
})

test('아직 응답이 없으면 빈 목록이다', () => {
  assert.deepEqual(factorRows(undefined), [])
  assert.equal(missingFactorNote(undefined), null)
})

test('값이 빈 팩터를 한 줄로 알리고 다 있으면 알리지 않는다', () => {
  const note = missingFactorNote(response([
    { factor: 'RETURN', period: 20, value: 4.1 },
    { factor: 'DRAWDOWN', period: 60, value: null },
    { factor: 'VOLATILITY', period: 20, value: null },
  ]))

  assert.equal(note, '봉이 기간을 채우지 못해 아직 값이 없습니다: 낙폭 60, 변동성 20')
  assert.equal(missingFactorNote(response([{ factor: 'RSI', period: 14, value: 62.1 }])), null)
})

const tickerMap = (entries) => new Map(entries.map(([id, tradeAmount]) => [id, { previousClose: null, lastClose: null, tradeAmount }]))

test('거래대금 순위는 값이 있는 종목끼리만 센다', () => {
  const tickers = tickerMap([['a', 300], ['b', 900], ['c', 100], ['d', null]])

  assert.deepEqual(tradeAmountRank(tickers, 'b'), { position: 1, total: 3 })
  assert.deepEqual(tradeAmountRank(tickers, 'a'), { position: 2, total: 3 })
  assert.deepEqual(tradeAmountRank(tickers, 'c'), { position: 3, total: 3 })
})

test('거래대금을 모르는 종목과 아직 못 받은 목록은 순위를 만들지 않는다', () => {
  assert.equal(tradeAmountRank(tickerMap([['a', 300], ['d', null]]), 'd'), null)
  assert.equal(tradeAmountRank(tickerMap([['a', 300]]), 'zz'), null)
  assert.equal(tradeAmountRank(undefined, 'a'), null)
})
