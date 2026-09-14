import type { BacktestTrade } from './types'

/*
 * 체결을 캔들 위 마커로 옮긴다. 계산은 없고 자리와 색만 정한다.
 * 어떤 종목을 보여 줄지도 여기서 정해 화면이 규칙을 다시 쓰지 않게 한다.
 */

export type TradeMarker = {
  /* lightweight-charts 가 쓰는 초 단위 시각. 봉과 같은 축이다. */
  time: number
  side: 'BUY' | 'SELL'
  price: number
}

/* 체결이 가장 많은 종목. 전략이 가장 오래 들고 있던 종목이라 차트로 보기에 알맞다. */
export function busiestMarket(trades: readonly BacktestTrade[]): string | null {
  const counts = new Map<string, number>()
  for (const trade of trades) {
    counts.set(trade.marketCode, (counts.get(trade.marketCode) ?? 0) + 1)
  }
  let best: string | null = null
  let bestCount = 0
  for (const [marketCode, count] of counts) {
    /* 수가 같으면 마켓 코드 순으로 정해 매번 같은 종목이 뽑히게 한다. */
    if (count > bestCount || (count === bestCount && best !== null && marketCode < best)) {
      best = marketCode
      bestCount = count
    }
  }
  return best
}

export function markersFor(trades: readonly BacktestTrade[], marketCode: string): TradeMarker[] {
  return trades
    .filter((trade) => trade.marketCode === marketCode)
    .map((trade) => ({ time: Math.floor(trade.filledAt.getTime() / 1000), side: trade.side, price: trade.executedPrice }))
    .sort((left, right) => left.time - right.time)
}
