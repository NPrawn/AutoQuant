import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchAssetFactors, fetchCandles, fetchChartAssets, fetchRecentCandles } from './api'
import { appendTrade, mergeCandles, upsertLive } from './model'
import type { CandleInterval } from './model'
import { connectPriceStream } from './priceStream'
import { connectMarketStream } from './stream'
import { summarizeDay, tickerFromDailyCandles } from './ticker'
import type { Ticker } from './ticker'
import { toCandle, toChartAsset } from './types'
import type { ChartAsset, LiveCandle, Trade } from './types'
import type { Timeframe } from '../strategy/types'

export function useChartAssets() {
  return useQuery({ queryKey: ['chart-assets'], queryFn: ({ signal }) => fetchChartAssets(signal), select: (items) => items.map(toChartAsset) })
}

/* 이 시간 안에 STOMP 가 붙지 않으면 확정 봉만 먼저 조회한다. */
const streamFallbackMs = 4000

// 종목별 keyed 화면에서 사용한다. 재연결마다 새 조회를 만들어 이전 연결의 늦은 응답을 격리한다.
export function useMarketFeed(asset: ChartAsset, interval: CandleInterval) {
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
  }, interval), [asset, interval])

  /*
   * 연결이 늦거나 막히면 조회를 먼저 켠다. 확정 봉은 REST 로 오므로 실시간이 없어도 차트는 보여야 한다.
   * 연결되면 generation 이 올라 다시 조회한다.
   */
  useEffect(() => {
    if (connected || generation > 0) return
    const timer = setTimeout(() => setGeneration((previous) => (previous === 0 ? 1 : previous)), streamFallbackMs)
    return () => clearTimeout(timer)
  }, [connected, generation])

  const history = useQuery({
    queryKey: ['market-candles', asset.id, interval, generation],
    queryFn: ({ signal }) => fetchRecentCandles(asset.id, interval, signal),
    enabled: generation > 0,
    staleTime: Infinity,
    // 확정/REST 보정 결과를 반영한다. 서버는 이미 저장된 선택 간격만 조회한다.
    refetchInterval: 60_000,
    gcTime: 0,
  })
  const merged = useMemo(() => history.data ? mergeCandles(history.data.candles, live) : [], [history.data, live])
  const candles = useMemo(() => merged.map(toCandle), [merged])
  return { history, connected, candles, rawCandles: merged, trades }
}

/*
 * 목록에 보일 종목별 전일 종가. 등락을 계산하려면 기준가가 필요한데 체결 스트림은 현재가만 준다.
 * 일봉 두 개면 충분해 limit 을 2 로 줄여 받는다. 한 종목이 실패해도 나머지는 보여야 하므로
 * allSettled 로 모으고 실패한 종목만 비운다.
 */
export const marketTickersQueryKey = ['market-tickers'] as const

async function fetchTickers(assets: readonly ChartAsset[], signal: AbortSignal): Promise<ReadonlyMap<string, Ticker>> {
  const results = await Promise.allSettled(assets.map((asset) => fetchCandles(asset.id, 1440, 2, signal)))
  const tickers = new Map<string, Ticker>()
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      tickers.set(assets[index].id, tickerFromDailyCandles(result.value.candles))
    }
  })
  return tickers
}

export function useMarketTickers(assets: readonly ChartAsset[]) {
  const assetKey = assets.map((asset) => asset.id).join(',')
  return useQuery({
    queryKey: [...marketTickersQueryKey, assetKey],
    queryFn: ({ signal }) => fetchTickers(assets, signal),
    enabled: assets.length > 0,
    refetchInterval: 60_000,
  })
}

/* 하루 고가·저가·거래량. 1시간봉 24개가 정확히 24시간이다. */
export function useDayStats(assetId: string) {
  return useQuery({
    queryKey: ['market-day-stats', assetId],
    queryFn: ({ signal }) => fetchCandles(assetId, 60, 24, signal),
    select: (response) => summarizeDay(response.candles),
    refetchInterval: 60_000,
  })
}

export type LivePrice = {
  price: string
  at: Date
}

/*
 * 여러 종목의 현재가를 연결 하나로 받는다. 배열 참조가 바뀔 때 연결을 다시 만드므로 호출자가
 * useMemo 로 고정한 배열을 넘긴다. 연결이 없으면 빈 맵이고 화면은 마지막 봉 종가로 대신한다.
 */
export function useLivePrices(assets: readonly ChartAsset[]) {
  const [prices, setPrices] = useState<ReadonlyMap<string, LivePrice>>(new Map())
  const [connected, setConnected] = useState(false)

  useEffect(
    () =>
      connectPriceStream(assets, {
        onConnected: () => setConnected(true),
        onDisconnected: () => setConnected(false),
        onTrade: (trade) =>
          setPrices((previous) => {
            const next = new Map(previous)
            next.set(trade.assetId, { price: trade.price, at: new Date(trade.tradedAtUnixMilli) })
            return next
          }),
      }),
    [assets],
  )

  return { prices, connected }
}

/* 종목의 현재 팩터 값. 봉이 바뀌어야 값이 바뀌므로 1분마다 다시 읽는다. */
export function useAssetFactors(assetId: string, timeframe: Timeframe) {
  return useQuery({
    queryKey: ['asset-factors', assetId, timeframe],
    queryFn: ({ signal }) => fetchAssetFactors(assetId, timeframe, signal),
    refetchInterval: 60_000,
  })
}

/* 지난 구간을 되짚는 화면용. 실시간 구독 없이 확정 봉만 받는다. */
export function useCandleHistory(assetId: string | null, interval: CandleInterval) {
  return useQuery({
    queryKey: ['market-candle-history', assetId, interval],
    queryFn: ({ signal }) => fetchRecentCandles(assetId ?? '', interval, signal),
    enabled: assetId !== null,
    select: (response) => response.candles.map(toCandle),
    staleTime: 60_000,
  })
}
