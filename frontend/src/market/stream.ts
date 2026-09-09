import { Client } from '@stomp/stompjs'
import { parseLiveCandle, parseTrade } from './model.ts'
import type { CandleInterval } from './model.ts'
import type { ChartAsset, LiveCandle, Trade } from './types.ts'

type Callbacks = {
  onConnected: () => void
  onDisconnected: () => void
  onTrade: (trade: Trade) => void
  onCandle: (candle: LiveCandle) => void
}

export function connectMarketStream(asset: ChartAsset, callbacks: Callbacks, interval: CandleInterval = 1): () => void {
  let disposed = false
  const url = new URL('/ws', window.location.href)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  const client = new Client({
    brokerURL: url.href,
    reconnectDelay: 3000,
    connectionTimeout: 10000,
    // 내장 simple broker는 heartbeat/receipt를 제공하지 않는다.
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    onWebSocketClose: () => { if (!disposed) callbacks.onDisconnected() },
    onStompError: () => { if (!disposed) callbacks.onDisconnected() },
    onConnect: () => {
      if (disposed) return
      client.subscribe(`/topic/markets/${asset.marketCode}/trades`, (message) => {
        const trade = parseTrade(message.body, asset)
        if (!disposed && trade) callbacks.onTrade(trade)
      })
      client.subscribe(`/topic/markets/${asset.marketCode}/candles/${interval}m`, (message) => {
        const candle = parseLiveCandle(message.body, asset, interval)
        if (!disposed && candle) callbacks.onCandle(candle)
      })
      // SUBSCRIBE 전송 후 REST 조회를 시작한다. 조회 중 수신값은 별도 버퍼에 유지한다.
      callbacks.onConnected()
    },
  })
  client.activate()
  return () => {
    disposed = true
    // 연결을 닫으면 두 구독도 함께 해제된다. 종목 전환·로그아웃·언마운트에서 호출한다.
    void client.deactivate({ force: true })
  }
}
