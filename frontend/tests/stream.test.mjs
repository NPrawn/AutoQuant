import assert from 'node:assert/strict'
import { mock, test } from 'node:test'

let client
class FakeClient {
  subscriptions = []
  constructor(options) { this.options = options; client = this }
  activate() { this.active = true }
  subscribe(destination, callback) { this.subscriptions.push({ destination, callback }) }
  deactivate() { this.active = false; return Promise.resolve() }
}
mock.module('@stomp/stompjs', { exports: { Client: FakeClient } })
const { connectMarketStream } = await import('../src/market/stream.ts')
globalThis.window = { location: { href: 'https://test.example/chart' } }

test('한 연결의 두 종목 토픽 구독 후 조회를 시작하고 재연결 시 다시 구독한다', () => {
  let ready = 0
  let disconnected = 0
  const stop = connectMarketStream({ id: 'asset', marketCode: 'KRW-BTC', name: '비트코인' }, {
    onConnected: () => { assert.equal(client.subscriptions.length % 2, 0); ready++ },
    onDisconnected: () => disconnected++, onTrade: () => {}, onCandle: () => {},
  })
  assert.equal(client.options.brokerURL, 'wss://test.example/ws')
  client.options.onConnect()
  assert.deepEqual(client.subscriptions.map((item) => item.destination), ['/topic/markets/KRW-BTC/trades', '/topic/markets/KRW-BTC/candles/1m'])
  client.options.onWebSocketClose()
  client.options.onConnect()
  assert.equal(disconnected, 1)
  assert.equal(ready, 2)
  stop()
  assert.equal(client.active, false)
  client.options.onConnect()
  assert.equal(ready, 2)
})

test('종목 전환으로 종료한 연결의 늦은 메시지는 화면으로 전달하지 않는다', () => {
  let received = 0
  const stop = connectMarketStream({ id: 'asset', marketCode: 'KRW-ETH', name: '이더리움' }, {
    onConnected: () => {}, onDisconnected: () => {}, onTrade: () => received++, onCandle: () => {},
  })
  client.options.onConnect()
  const message = { body: JSON.stringify({ assetId: 'asset', marketCode: 'KRW-ETH', price: '100', volume: '1', side: 'ASK', tradedAtUnixMilli: 1800000000000, sequenceId: 1 }) }
  client.subscriptions[0].callback(message)
  assert.equal(received, 1)
  stop()
  client.subscriptions[0].callback(message)
  assert.equal(received, 1)
})

test('간격 전환 시 선택한 봉 토픽만 구독하고 체결 토픽은 유지한다', () => {
  const stop = connectMarketStream({ id: 'asset', marketCode: 'KRW-BTC', name: '비트코인' }, {
    onConnected: () => {}, onDisconnected: () => {}, onTrade: () => {}, onCandle: () => {},
  }, 240)
  client.options.onConnect()
  assert.deepEqual(client.subscriptions.map((item) => item.destination), [
    '/topic/markets/KRW-BTC/trades', '/topic/markets/KRW-BTC/candles/240m',
  ])
  stop()
})
