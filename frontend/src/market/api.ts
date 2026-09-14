import { request } from '../api/client'
import type { AssetFactorsResponse, ChartAssetResponse, RecentCandlesResponse } from './types'
import { CANDLE_LIMIT } from './model'
import type { CandleInterval } from './model'
import type { Timeframe } from '../strategy/types'

export const fetchChartAssets = (signal: AbortSignal) => request<ChartAssetResponse[]>('/assets', { signal })
export const fetchRecentCandles = (assetId: string, interval: CandleInterval, signal: AbortSignal) =>
  fetchCandles(assetId, interval, CANDLE_LIMIT, signal)

/* 등락과 하루 통계는 봉 몇 개면 충분해 limit 을 줄여 받는다. */
export const fetchCandles = (assetId: string, interval: CandleInterval, limit: number, signal: AbortSignal) =>
  request<RecentCandlesResponse>(`/assets/${encodeURIComponent(assetId)}/candles/${interval}m?limit=${limit}`, { signal })

/* 팩터는 화면이 계산하지 않는다. 봉 주기만 넘기고 서버가 정한 기간의 값을 그대로 받는다. */
export const fetchAssetFactors = (assetId: string, timeframe: Timeframe, signal: AbortSignal) =>
  request<AssetFactorsResponse>(`/assets/${encodeURIComponent(assetId)}/factors?timeframe=${timeframe}`, { signal })
