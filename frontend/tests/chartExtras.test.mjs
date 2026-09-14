import assert from 'node:assert/strict'
import { test } from 'node:test'
import { sparkFromEquity } from '../src/simulation/sparkline.ts'
import { busiestMarket, markersFor } from '../src/backtest/tradeMarkers.ts'

const point = (totalAssetValue) => ({
  valuationAt: new Date(0), totalAssetValue, returnRate: 0, drawdownRate: 0,
})

test('평가 시점이 둘 미만이면 그릴 곡선이 없다', () => {
  assert.equal(sparkFromEquity([]), null)
  assert.equal(sparkFromEquity([point(100)]), null)
})

test('곡선은 가장 낮은 값과 높은 값 사이를 채운다', () => {
  const spark = sparkFromEquity([point(100), point(200)])

  assert.equal(spark.line, 'M0 128L600 12')
  assert.equal(spark.area, 'M0 128L600 12L600 140L0 140Z')
  assert.deepEqual([spark.lastX, spark.lastY], [600, 12])
  assert.equal(spark.rising, true)
})

test('값이 모두 같으면 가운데에 평평하게 둔다', () => {
  const spark = sparkFromEquity([point(100), point(100), point(100)])

  assert.equal(spark.line, 'M0 70L300 70L600 70')
  assert.equal(spark.rising, true)
})

test('마지막이 처음보다 낮으면 하락으로 본다', () => {
  assert.equal(sparkFromEquity([point(200), point(100)]).rising, false)
})

const trade = (marketCode, side, filledAt, executedPrice) => ({
  id: `${marketCode}-${filledAt}`, marketCode, side, filledAt: new Date(filledAt),
  executedPrice, quantity: 1, notional: executedPrice, feeAmount: 0, reason: null,
})

test('체결이 가장 많은 종목을 고르고 없으면 비운다', () => {
  const trades = [
    trade('KRW-BTC', 'BUY', '2026-01-01T00:00:00Z', 100),
    trade('KRW-ETH', 'BUY', '2026-01-02T00:00:00Z', 200),
    trade('KRW-BTC', 'SELL', '2026-01-03T00:00:00Z', 110),
  ]

  assert.equal(busiestMarket(trades), 'KRW-BTC')
  assert.equal(busiestMarket([]), null)
})

test('체결 수가 같으면 마켓 코드 순으로 정해 매번 같은 종목이 뽑힌다', () => {
  const trades = [
    trade('KRW-ETH', 'BUY', '2026-01-01T00:00:00Z', 200),
    trade('KRW-BTC', 'BUY', '2026-01-02T00:00:00Z', 100),
  ]

  assert.equal(busiestMarket(trades), 'KRW-BTC')
  assert.equal(busiestMarket([...trades].reverse()), 'KRW-BTC')
})

test('마커는 고른 종목만 시간 오름차순으로 남긴다', () => {
  const trades = [
    trade('KRW-BTC', 'SELL', '2026-01-03T00:00:00Z', 110),
    trade('KRW-ETH', 'BUY', '2026-01-02T00:00:00Z', 200),
    trade('KRW-BTC', 'BUY', '2026-01-01T00:00:00Z', 100),
  ]

  assert.deepEqual(markersFor(trades, 'KRW-BTC'), [
    { time: Date.parse('2026-01-01T00:00:00Z') / 1000, side: 'BUY', price: 100 },
    { time: Date.parse('2026-01-03T00:00:00Z') / 1000, side: 'SELL', price: 110 },
  ])
  assert.deepEqual(markersFor(trades, 'KRW-XRP'), [])
})

test('계산 실패는 서버가 준 원인별로 다른 문장을 보인다', async () => {
  const { describeFailedRun } = await import('../src/backtest/runErrorMessage.ts')

  assert.match(describeFailedRun('INSUFFICIENT_CANDLES').title, /캔들이 부족/)
  assert.match(describeFailedRun('EMPTY_UNIVERSE').title, /후보에 남는 종목이 없/)
  assert.match(describeFailedRun('ENGINE_ERROR').title, /계산 중 오류/)
  /* 옛 실행처럼 원인이 비어 오면 원인을 단정하지 않는다 */
  assert.equal(describeFailedRun(null).title, '계산을 마치지 못했습니다')
})
