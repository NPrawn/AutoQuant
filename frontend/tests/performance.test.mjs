import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  chartFrame, closedPath, deepestDrawdown, drawdownDomain, equityDomain, formatMonth, indexAt, indexBenchmark,
  indexEquity, linePath, linearScale, monthTicks, niceTicks, rebalanceCycles, shortSymbol, timeScale,
} from '../src/backtest/performance.ts'

const day = (iso, value, drawdownRate = 0) => ({
  valuationAt: new Date(iso),
  totalAssetValue: value,
  returnRate: value / 3_000_000 - 1,
  drawdownRate,
})

const points = [
  day('2026-01-01T00:00:00Z', 3_000_000),
  day('2026-01-02T00:00:00Z', 3_150_000),
  day('2026-01-03T00:00:00Z', 2_700_000, 0.1428),
  day('2026-01-04T00:00:00Z', 3_300_000),
]

test('지수는 첫 평가 시점을 100 으로 두고 비율로 옮긴다', () => {
  const indexed = indexEquity(points)
  assert.equal(indexed[0].value, 100)
  assert.equal(indexed[1].value, 105)
  assert.equal(indexed[2].value, 90)
  assert.equal(indexed[3].value, 110)
  assert.deepEqual(indexEquity([]), [])
})

test('벤치마크는 평가 시각 앞에 시작한 마지막 봉의 종가를 쓴다', () => {
  const candle = (iso, close) => ({ openTimeUnixMilli: new Date(iso).getTime(), closePrice: String(close) })
  /* 봉은 하루 늦게 닫힌다. 1/2 00:00 평가는 1/1 봉 종가와 견준다. 같은 시각에 시작한 봉은 아직 열려 있다. */
  const benchmark = indexBenchmark(
    [candle('2025-12-31T00:00:00Z', 200), candle('2026-01-01T00:00:00Z', 220), candle('2026-01-02T00:00:00Z', 198), candle('2026-01-03T00:00:00Z', 242)],
    points,
  )
  assert.deepEqual(
    benchmark.map((point) => [point.at.toISOString().slice(0, 10), point.value]),
    [['2026-01-01', 100], ['2026-01-02', 110], ['2026-01-03', 99], ['2026-01-04', 121]],
  )
})

test('앞선 봉이 없는 평가 시점은 벤치마크에서 빠진다', () => {
  const candle = (iso, close) => ({ openTimeUnixMilli: new Date(iso).getTime(), closePrice: String(close) })
  const benchmark = indexBenchmark([candle('2026-01-02T00:00:00Z', 100), candle('2026-01-03T00:00:00Z', 150)], points)
  assert.deepEqual(benchmark.map((point) => point.at.toISOString().slice(0, 10)), ['2026-01-03', '2026-01-04'])
  assert.equal(benchmark[0].value, 100)
  assert.equal(benchmark[1].value, 150)
  assert.deepEqual(indexBenchmark([], points), [])
})

test('같은 시각의 체결이 한 회차이고 회차 수익은 직전 회차와 견준다', () => {
  const trade = (iso, side, marketCode) => ({
    id: `${iso}-${marketCode}`, marketCode, side, filledAt: new Date(iso),
    executedPrice: 1, quantity: 1, notional: 1, feeAmount: 0, reason: null,
  })
  const cycles = rebalanceCycles(
    [
      trade('2026-01-03T00:00:00Z', 'SELL', 'KRW-ETH'),
      trade('2026-01-01T00:00:00Z', 'BUY', 'KRW-BTC'),
      trade('2026-01-01T00:00:00Z', 'BUY', 'KRW-ETH'),
      trade('2026-01-03T00:00:00Z', 'BUY', 'KRW-SOL'),
    ],
    indexEquity(points),
  )
  assert.equal(cycles.length, 2)
  assert.deepEqual(cycles[0].bought, ['BTC', 'ETH'])
  assert.deepEqual(cycles[0].sold, [])
  assert.equal(cycles[0].equityIndex, 100)
  assert.equal(cycles[0].returnRate, 0)
  assert.deepEqual(cycles[1].bought, ['SOL'])
  assert.deepEqual(cycles[1].sold, ['ETH'])
  assert.equal(cycles[1].equityIndex, 90)
  assert.ok(Math.abs(cycles[1].returnRate - -10) < 1e-9)
  assert.deepEqual(rebalanceCycles([trade('2026-01-01T00:00:00Z', 'BUY', 'KRW-BTC')], []), [])
})

test('회차 시각의 지수는 그 시각까지의 마지막 값이고 앞서면 첫 값이다', () => {
  const indexed = indexEquity(points)
  assert.equal(indexAt(indexed, new Date('2026-01-02T12:00:00Z').getTime()), 105)
  assert.equal(indexAt(indexed, new Date('2025-12-31T00:00:00Z').getTime()), 100)
  assert.equal(shortSymbol('KRW-BTC'), 'BTC')
  assert.equal(shortSymbol('BTC'), 'BTC')
})

test('시간축은 첫 시점과 마지막 시점을 그림 영역 양 끝에 놓는다', () => {
  const x = timeScale(indexEquity(points))
  assert.equal(x(points[0].valuationAt.getTime()), chartFrame.left)
  assert.equal(x(points[3].valuationAt.getTime()), chartFrame.right)
})

test('지수 범위는 두 선을 모두 담고 여백을 두며 낙폭 바닥은 5% 를 밑돌지 않는다', () => {
  const domain = equityDomain([indexEquity(points), [{ at: points[0].valuationAt, value: 80 }]])
  assert.ok(domain.lo < 80 && domain.hi > 110)
  assert.deepEqual(equityDomain([[]]), { lo: 90, hi: 110 })
  assert.ok(drawdownDomain([]) >= 0.05)
  assert.ok(drawdownDomain([{ at: points[2].valuationAt, rate: 0.5 }]) > 0.5)
  const y = linearScale(0, 1, 320, 410)
  assert.equal(y(0), 320)
  assert.equal(y(1), 410)
  assert.equal(linearScale(5, 5, 0, 100)(5), 50)
})

test('눈금은 1·2·2.5·5 배수로 대여섯 개가 나온다', () => {
  assert.deepEqual(niceTicks(94, 207, 6), [100, 120, 140, 160, 180, 200])
  assert.deepEqual(niceTicks(94, 207), [100, 125, 150, 175, 200])
  assert.deepEqual(niceTicks(0, 0.28, 3), [0, 0.1, 0.2])
  assert.deepEqual(niceTicks(3, 3), [3])
})

test('월 눈금은 기간 안의 매월 1 일이고 많으면 건너뛴다', () => {
  const ticks = monthTicks(new Date('2025-01-03T00:00:00Z'), new Date('2025-04-20T00:00:00Z'))
  assert.deepEqual(ticks.map(formatMonth), ['2025-02', '2025-03', '2025-04'])
  const long = monthTicks(new Date('2018-01-15T00:00:00Z'), new Date('2026-09-18T00:00:00Z'))
  assert.ok(long.length <= 9)
})

test('가장 깊은 낙폭과 경로 문자열', () => {
  const deepest = deepestDrawdown([{ at: points[1].valuationAt, rate: 0.02 }, { at: points[2].valuationAt, rate: 0.14 }])
  assert.equal(deepest.rate, 0.14)
  assert.equal(deepestDrawdown([]), null)
  assert.equal(linePath([{ x: 1, y: 2 }, { x: 3.25, y: 4 }]), 'M1.0,2.0L3.3,4.0')
  assert.equal(closedPath([{ x: 1, y: 2 }, { x: 3, y: 4 }], 10), 'M1.0,2.0L3.0,4.0L3.0,10.0L1.0,10.0Z')
  assert.equal(closedPath([], 10), '')
})
