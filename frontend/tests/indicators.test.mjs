import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  bollingerBands, lastValue, movingAverage, relativeStrengthIndex, volumeBars,
} from '../src/market/indicators.ts'

const candlesFrom = (closes, volumes = []) =>
  closes.map((close, index) => ({
    time: index, open: index === 0 ? close : closes[index - 1], high: close, low: close, close,
    volume: volumes[index] ?? 1,
  }))

test('이동평균은 기간이 채워진 뒤부터 만든다', () => {
  const candles = candlesFrom([1, 2, 3, 4, 5])
  const points = movingAverage(candles, 3)
  assert.deepEqual(points, [
    { time: 2, value: 2 },
    { time: 3, value: 3 },
    { time: 4, value: 4 },
  ])
})

test('봉이 기간보다 적으면 이동평균을 만들지 않는다', () => {
  assert.deepEqual(movingAverage(candlesFrom([1, 2]), 3), [])
  assert.deepEqual(movingAverage(candlesFrom([1, 2, 3]), 0), [])
})

test('볼린저밴드는 모집단 표준편차를 쓰고 중앙선은 이동평균과 같다', () => {
  const candles = candlesFrom([2, 4, 4, 4, 5, 5, 7, 9])
  const bands = bollingerBands(candles, 8, 2)
  /* 평균 5, 모집단 표준편차 2 인 고전적인 예시다. */
  assert.equal(bands.middle.length, 1)
  assert.equal(bands.middle[0].value, 5)
  assert.equal(bands.upper[0].value, 9)
  assert.equal(bands.lower[0].value, 1)
  assert.deepEqual(bollingerBands(candlesFrom([1, 2]), 5), { upper: [], middle: [], lower: [] })
})

test('중앙선은 같은 기간 이동평균과 일치한다', () => {
  const candles = candlesFrom([3, 7, 5, 9, 11, 4, 8])
  const bands = bollingerBands(candles, 3, 2)
  const average = movingAverage(candles, 3)
  assert.deepEqual(bands.middle, average)
})

test('RSI 는 기간 다음 봉부터 시작하고 0과 100 사이에 머문다', () => {
  const candles = candlesFrom([44, 44.3, 44.1, 44.2, 44.6, 43.4, 44.2, 44.6, 43.4, 44.2, 44.6, 43.4, 44.2, 44.6, 45.1, 44.9])
  const points = relativeStrengthIndex(candles, 14)
  assert.equal(points.length, candles.length - 14)
  assert.equal(points[0].time, 14)
  for (const point of points) {
    assert.ok(point.value >= 0 && point.value <= 100, `RSI ${point.value} 가 범위를 벗어남`)
  }
})

test('한 방향으로만 움직인 구간의 RSI 는 끝값이다', () => {
  const rising = relativeStrengthIndex(candlesFrom([1, 2, 3, 4, 5, 6]), 3)
  assert.equal(rising.at(-1).value, 100)
  const falling = relativeStrengthIndex(candlesFrom([6, 5, 4, 3, 2, 1]), 3)
  assert.equal(falling.at(-1).value, 0)
})

test('봉이 기간 이하면 RSI 를 만들지 않는다', () => {
  assert.deepEqual(relativeStrengthIndex(candlesFrom([1, 2, 3]), 3), [])
})

test('거래량 막대는 종가가 시가 이상이면 상승으로 본다', () => {
  const bars = volumeBars(candlesFrom([10, 12, 11], [5, 6, 7]))
  assert.deepEqual(bars, [
    { time: 0, value: 5, rising: true },
    { time: 1, value: 6, rising: true },
    { time: 2, value: 7, rising: false },
  ])
})

test('마지막 값은 범례가 쓰고 비어 있으면 null 이다', () => {
  assert.equal(lastValue([{ time: 1, value: 3 }, { time: 2, value: 4 }]), 4)
  assert.equal(lastValue([]), null)
})
