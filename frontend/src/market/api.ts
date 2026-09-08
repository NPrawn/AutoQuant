import { request } from '../api/client'
import type { ChartAssetResponse, RecentCandlesResponse } from './types'
import { CANDLE_LIMIT } from './model'
import type { CandleInterval } from './model'

export const fetchChartAssets = (signal: AbortSignal) => request<ChartAssetResponse[]>('/assets', { signal })
export const fetchRecentCandles = (assetId: string, interval: CandleInterval, signal: AbortSignal) =>
  request<RecentCandlesResponse>(`/assets/${encodeURIComponent(assetId)}/candles/${interval}m?limit=${CANDLE_LIMIT}`, { signal })
