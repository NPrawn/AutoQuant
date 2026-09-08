import type { CandleResponse, ChartAsset, LiveCandle, Trade } from './types.ts'

export const CANDLE_LIMIT = 1000
export const TRADE_LIMIT = 30
export const CANDLE_INTERVALS = [1, 2, 5, 15, 60, 240, 1440] as const
export type CandleInterval = typeof CANDLE_INTERVALS[number]
export const intervalLabel = (interval: CandleInterval) => interval < 60 ? `${interval}분` : `${interval / 60}시간`

export function mergeCandles(confirmed: CandleResponse[], live: LiveCandle[]): CandleResponse[] {
  const candles = new Map(confirmed.map((candle) => [candle.openTimeUnixMilli, candle]))
  const confirmedEnd = confirmed.at(-1)?.openTimeUnixMilli ?? -1
  for (const candle of live) {
    // 조회로 확정된 봉을 지연 수신한 진행 봉으로 되돌리지 않는다.
    if (candle.openTimeUnixMilli > confirmedEnd) candles.set(candle.openTimeUnixMilli, candle)
  }
  return [...candles.values()].sort((a, b) => a.openTimeUnixMilli - b.openTimeUnixMilli).slice(-CANDLE_LIMIT)
}

export function upsertLive(candles: LiveCandle[], incoming: LiveCandle): LiveCandle[] {
  const previous = candles.find((candle) => candle.openTimeUnixMilli === incoming.openTimeUnixMilli)
  if (previous && BigInt(previous.lastSequenceId) >= BigInt(incoming.lastSequenceId)) return candles
  return [...candles.filter((candle) => candle.openTimeUnixMilli !== incoming.openTimeUnixMilli), incoming]
    .sort((a, b) => a.openTimeUnixMilli - b.openTimeUnixMilli).slice(-CANDLE_LIMIT)
}

export function appendTrade(trades: Trade[], incoming: Trade): Trade[] {
  if (trades.some((trade) => trade.sequenceId === incoming.sequenceId)) return trades
  return [...trades, incoming].sort((a, b) => b.tradedAtUnixMilli - a.tradedAtUnixMilli
    || (BigInt(a.sequenceId) < BigInt(b.sequenceId) ? 1 : -1)).slice(0, TRADE_LIMIT)
}

function decode(body: string, asset: ChartAsset): Record<string, unknown> | null {
  try {
    // JSON의 int64 순번을 Number로 변환하기 전에 보존한다. 가격·수량은 원래 문자열이다.
    const value: unknown = JSON.parse(body.replace(/("(?:sequenceId|lastSequenceId)"\s*:\s*)(\d+)(?=\s*[,}])/g, '$1"$2"'))
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null
    const record = value as Record<string, unknown>
    return record.assetId === asset.id && record.marketCode === asset.marketCode ? record : null
  } catch {
    return null
  }
}
const decimal = (value: unknown) => typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value) && Number.isFinite(Number(value))
const timestamp = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0
const sequence = (value: unknown) => typeof value === 'string' && /^[1-9]\d*$/.test(value)

export function parseTrade(body: string, asset: ChartAsset): Trade | null {
  const value = decode(body, asset)
  return value && decimal(value.price) && Number(value.price) > 0 && decimal(value.volume) && Number(value.volume) > 0
    && (value.side === 'ASK' || value.side === 'BID') && timestamp(value.tradedAtUnixMilli) && sequence(value.sequenceId)
    ? value as Trade : null
}

export function parseLiveCandle(body: string, asset: ChartAsset, interval: CandleInterval = 1): LiveCandle | null {
  const value = decode(body, asset)
  if (!value || value.intervalMinutes !== interval || !timestamp(value.openTimeUnixMilli) || value.openTimeUnixMilli % (interval * 60000) !== 0
    || !timestamp(value.lastEventTimeUnixMilli) || !sequence(value.lastSequenceId)) return null
  if (!['openPrice', 'highPrice', 'lowPrice', 'closePrice', 'tradeVolume', 'tradeAmount'].every((key) => decimal(value[key]))) return null
  return value as LiveCandle
}
