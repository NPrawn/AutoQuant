import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  changeClass, changeRate, displayMarket, formatChangeRate, formatPrice, marketSymbol, matchesQuery,
  summarizeDay, tickerFromDailyCandles,
} from '../src/market/ticker.ts'

const candle = (open, high, low, close, volume) => ({
  openTimeUnixMilli: 0, openPrice: open, highPrice: high, lowPrice: low, closePrice: close,
  tradeVolume: volume, tradeAmount: '0', lastEventTimeUnixMilli: 0,
})

test('전일 종가는 마지막에서 두 번째 봉이고 봉이 하나면 비교할 대상이 없다', () => {
  assert.deepEqual(tickerFromDailyCandles([candle('1', '1', '1', '100', '1'), candle('1', '1', '1', '110', '1')]), {
    previousClose: 100, lastClose: '110', tradeAmount: 0,
  })
  assert.deepEqual(tickerFromDailyCandles([candle('1', '1', '1', '110', '1')]), { previousClose: null, lastClose: '110', tradeAmount: 0 })
  assert.deepEqual(tickerFromDailyCandles([]), { previousClose: null, lastClose: null, tradeAmount: null })
})

test('기준가가 없거나 0 이면 등락을 계산하지 않는다', () => {
  assert.ok(Math.abs(changeRate('110', 100) - 0.1) < 1e-12)
  assert.ok(Math.abs(changeRate('90', 100) + 0.1) < 1e-12)
  assert.equal(changeRate('110', null), null)
  assert.equal(changeRate(null, 100), null)
  assert.equal(changeRate('110', 0), null)
})

test('가격 표기는 문자열을 다듬어 정밀도를 잃지 않는다', () => {
  assert.equal(formatPrice('128450000'), '128,450,000')
  assert.equal(formatPrice('2.69000000'), '2.69')
  assert.equal(formatPrice('0.00002840'), '0.0000284')
  assert.equal(formatPrice('1234.50000000'), '1,234.5')
  assert.equal(formatPrice('100.00000000'), '100')
  assert.equal(formatPrice(null), '—')
})

test('등락 표기와 색은 색 계약대로 빨강이 상승이다', () => {
  assert.equal(formatChangeRate(0.0214), '+2.14%')
  assert.equal(formatChangeRate(-0.0086), '-0.86%')
  assert.equal(formatChangeRate(null), '—')
  assert.equal(changeClass(0.01), 'updown up')
  assert.equal(changeClass(-0.01), 'updown down')
  assert.equal(changeClass(0), '')
  assert.equal(changeClass(null), '')
})

test('하루 통계는 고가 최대, 저가 최소, 거래량 합이다', () => {
  const stats = summarizeDay([candle('1', '120', '90', '1', '1.5'), candle('1', '130', '95', '1', '2.25')])
  assert.deepEqual(stats, { high: '130', low: '90', volume: 3.75 })
  assert.equal(summarizeDay([]), null)
})

test('검색은 마켓 코드와 한글명 모두에서 대소문자 없이 찾는다', () => {
  const asset = { marketCode: 'KRW-BTC', name: '비트코인' }
  assert.equal(matchesQuery(asset, ''), true)
  assert.equal(matchesQuery(asset, '  '), true)
  assert.equal(matchesQuery(asset, 'btc'), true)
  assert.equal(matchesQuery(asset, '비트'), true)
  assert.equal(matchesQuery(asset, 'eth'), false)
})

test('마켓 코드는 화면에서 BTC/KRW 로 읽는다', () => {
  assert.equal(displayMarket('KRW-BTC'), 'BTC/KRW')
  assert.equal(marketSymbol('KRW-BTC'), 'BTC')
  // 모양이 다른 값은 건드리지 않고 그대로 보인다
  assert.equal(displayMarket('BTC'), 'BTC')
})
