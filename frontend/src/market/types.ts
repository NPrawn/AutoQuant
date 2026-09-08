export type ChartAssetResponse = { assetId: string; marketCode: string; koreanName: string }
export type ChartAsset = { id: string; marketCode: string; name: string }

export function toChartAsset(response: ChartAssetResponse): ChartAsset {
  return { id: response.assetId, marketCode: response.marketCode, name: response.koreanName }
}

export type CandleResponse = {
  openTimeUnixMilli: number
  openPrice: string
  highPrice: string
  lowPrice: string
  closePrice: string
  tradeVolume: string
  tradeAmount: string
  lastEventTimeUnixMilli: number
}
export type RecentCandlesResponse = { assetId: string; intervalMinutes: number; candles: CandleResponse[] }
export type LiveCandle = CandleResponse & { assetId: string; marketCode: string; intervalMinutes: number; lastSequenceId: string }
export type Trade = {
  assetId: string
  marketCode: string
  price: string
  volume: string
  side: 'ASK' | 'BID'
  tradedAtUnixMilli: number
  sequenceId: string
}
export type Candle = { time: number; open: number; high: number; low: number; close: number }

// 그리기에만 number를 사용한다. 체결 표시와 원본 응답의 금액은 문자열 그대로 유지한다.
export function toCandle(response: CandleResponse): Candle {
  return {
    time: response.openTimeUnixMilli / 1000,
    open: Number(response.openPrice), high: Number(response.highPrice),
    low: Number(response.lowPrice), close: Number(response.closePrice),
  }
}
