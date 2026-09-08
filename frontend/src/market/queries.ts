import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchChartAssets, fetchRecentCandles } from './api'
import { appendTrade, mergeCandles, upsertLive } from './model'
import { connectMarketStream } from './stream'
import { toCandle, toChartAsset } from './types'
import type { ChartAsset, LiveCandle, Trade } from './types'

export function useChartAssets() {
  return useQuery({ queryKey: ['chart-assets'], queryFn: ({ signal }) => fetchChartAssets(signal), select: (items) => items.map(toChartAsset) })
}

// 종목별 keyed 화면에서 사용한다. 재연결마다 새 조회를 만들어 이전 연결의 늦은 응답을 격리한다.
export function useMarketFeed(asset: ChartAsset) {
  const [generation, setGeneration] = useState(0)
  const [connected, setConnected] = useState(false)
  const [live, setLive] = useState<LiveCandle[]>([])
  const [trades, setTrades] = useState<Trade[]>([])
  useEffect(() => connectMarketStream(asset, {
    onConnected: () => {
      setLive([])
      setTrades([])
      setConnected(true)
      setGeneration((previous) => previous + 1)
    },
    onDisconnected: () => setConnected(false),
    onTrade: (trade) => setTrades((previous) => appendTrade(previous, trade)),
    onCandle: (candle) => setLive((previous) => upsertLive(previous, candle)),
  }), [asset])

  const history = useQuery({
    queryKey: ['market-candles', asset.id, generation],
    queryFn: ({ signal }) => fetchRecentCandles(asset.id, signal),
    enabled: generation > 0,
    staleTime: Infinity,
    gcTime: 0,
  })
  const candles = useMemo(() => history.data ? mergeCandles(history.data.candles, live).map(toCandle) : [], [history.data, live])
  return { history, connected, candles, trades }
}
