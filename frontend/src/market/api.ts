import { request } from '../api/client'
import type { ChartAssetResponse, RecentCandlesResponse } from './types'
import { CANDLE_LIMIT } from './model'

export const fetchChartAssets = (signal: AbortSignal) => request<ChartAssetResponse[]>('/assets', { signal })
export const fetchRecentCandles = (assetId: string, signal: AbortSignal) =>
  request<RecentCandlesResponse>(`/assets/${encodeURIComponent(assetId)}/candles/1m?limit=${CANDLE_LIMIT}`, { signal })
