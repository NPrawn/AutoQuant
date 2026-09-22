import assert from 'node:assert/strict'
import { test } from 'node:test'
import { appendTrade, mergeCandles, parseLiveCandle, parseTrade, upsertLive, CANDLE_INTERVALS, TRADE_LIMIT } from '../src/market/model.ts'

const asset = { id: '11111111-1111-1111-1111-111111111111', marketCode: 'KRW-BTC', name: '비트코인' }
const start = 1800000000000
const candle = (minute, sequence = '1') => ({ assetId: asset.id, marketCode: asset.marketCode, intervalMinutes: 1,
  openTimeUnixMilli: start + minute * 60000, openPrice: '100', highPrice: '101', lowPrice: '99', closePrice: '100',
  tradeVolume: '1', tradeAmount: '100', lastEventTimeUnixMilli: start + minute * 60000 + 1, lastSequenceId: sequence })
const trade = (sequenceId) => ({ assetId: asset.id, marketCode: asset.marketCode, price: '100.123456789012', volume: '0.000000000000000001', side: 'BID', tradedAtUnixMilli: start, sequenceId })

test('조회 중 버퍼링된 진행 봉은 확정 봉 뒤에 합치고 같은 분은 갱신한다', () => {
  const live = upsertLive([candle(1)], { ...candle(1, '2'), closePrice: '101' })
  assert.deepEqual(mergeCandles([candle(0)], live).map((item) => item.closePrice), ['100', '101'])
})
test('재연결 조회로 확정된 봉을 이전 진행 봉으로 덮어쓰지 않는다', () => {
  assert.equal(mergeCandles([{ ...candle(1), closePrice: '200' }], [candle(1), candle(2)])[0].closePrice, '200')
})
test('이전 순번은 무시하고 최신 1000개만 유지한다', () => {
  const live = [candle(1, '9007199254740993')]
  assert.equal(upsertLive(live, candle(1, '9007199254740992')), live)
  const result = mergeCandles(Array.from({ length: 1000 }, (_, index) => candle(index)), [candle(1000)])
  assert.equal(result.length, 1000)
  assert.equal(result[0].openTimeUnixMilli, start + 60000)
})
test('int64 순번과 금액을 정밀도 손실 없이 파싱한다', () => {
  const body = JSON.stringify(trade('9223372036854775806')).replace('"sequenceId":"9223372036854775806"', '"sequenceId":9223372036854775806')
  assert.equal(parseTrade(body, asset).sequenceId, '9223372036854775806')
  assert.equal(parseTrade(body, asset).price, '100.123456789012')
})
test('손상된 JSON과 다른 종목 및 잘못된 side·interval을 폐기한다', () => {
  assert.equal(parseTrade('{', asset), null)
  assert.equal(parseTrade(JSON.stringify(trade('1')), { ...asset, marketCode: 'KRW-ETH' }), null)
  assert.equal(parseTrade(JSON.stringify({ ...trade('1'), side: 'INVALID' }), asset), null)
  assert.equal(parseLiveCandle(JSON.stringify({ ...candle(0), intervalMinutes: 5 }), asset), null)
  assert.equal(parseLiveCandle(JSON.stringify(candle(0)), asset).openTimeUnixMilli, start)
})
test('최근 체결은 중복 없이 최신순으로 상한만큼만 유지한다', () => {
  let trades = []
  for (let index = 1; index <= 40; index++) trades = appendTrade(trades, trade(String(index)))
  assert.equal(trades.length, TRADE_LIMIT)
  assert.equal(trades[0].sequenceId, '40')
  assert.equal(appendTrade(trades, trade('40')), trades)
})

test('선택 간격과 UTC 경계가 일치하는 진행 봉만 반영한다', () => {
  for (const interval of CANDLE_INTERVALS) {
    const open = Math.floor(start / (interval * 60000)) * interval * 60000
    const value = { ...candle(0), intervalMinutes: interval, openTimeUnixMilli: open }
    assert.equal(parseLiveCandle(JSON.stringify(value), asset, interval).intervalMinutes, interval)
    assert.equal(parseLiveCandle(JSON.stringify({ ...value, openTimeUnixMilli: open + 1 }), asset, interval), null)
    assert.equal(parseLiveCandle(JSON.stringify(value), { ...asset, marketCode: 'KRW-ETH' }, interval), null)
  }
})
