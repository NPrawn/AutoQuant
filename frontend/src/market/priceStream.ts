import { Client } from '@stomp/stompjs'

import { parseTrade } from './model'
import type { ChartAsset, Trade } from './types'

type Callbacks = {
  onConnected: () => void
  onDisconnected: () => void
  onTrade: (trade: Trade) => void
}

/*
 * 보유 종목 여러 개의 체결을 연결 하나로 받는다. 차트의 connectMarketStream 은 종목 하나와
 * 봉 구독을 소유하는데, 모의투자는 현재가만 필요해 종목마다 체결 토픽만 구독한다.
 * 파싱과 검증은 market/model 의 것을 그대로 써서 두 화면이 같은 규칙으로 값을 거른다.
 */
export function connectPriceStream(assets: readonly ChartAsset[], callbacks: Callbacks): () => void {
  let disposed = false
  const url = new URL('/ws', window.location.href)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  const client = new Client({
    brokerURL: url.href,
    reconnectDelay: 3000,
    connectionTimeout: 10000,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    onWebSocketClose: () => {
      if (!disposed) callbacks.onDisconnected()
    },
    onStompError: () => {
      if (!disposed) callbacks.onDisconnected()
    },
    onConnect: () => {
      if (disposed) return
      for (const asset of assets) {
        client.subscribe(`/topic/markets/${asset.marketCode}/trades`, (message) => {
          const trade = parseTrade(message.body, asset)
          if (!disposed && trade) callbacks.onTrade(trade)
        })
      }
      callbacks.onConnected()
    },
  })
  if (assets.length > 0) {
    client.activate()
  }
  return () => {
    disposed = true
    void client.deactivate({ force: true })
  }
}
