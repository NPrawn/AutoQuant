import type { Candle } from './types'

/*
 * 차트에 그리는 보조지표. 표시 전용이고 전략 판단에 쓰이지 않는다. 전략이 실제로 쓰는 값은
 * 서버가 계산해 팩터 카드로 내려준다.
 *
 * 공통 규칙 하나. 기간이 채워지기 전 구간은 값을 만들지 않는다. 0 이나 첫 값으로 메우면
 * 차트 앞머리에 없는 추세가 그려진다.
 */
export type IndicatorPoint = {
  time: number
  value: number
}

export const indicatorKeys = ['ma', 'bb', 'vol', 'rsi'] as const
export type IndicatorKey = (typeof indicatorKeys)[number]

export const movingAveragePeriods = [5, 20, 60] as const
export const bollingerPeriod = 20
export const bollingerMultiplier = 2
export const rsiPeriod = 14

export function movingAverage(candles: readonly Candle[], period: number): IndicatorPoint[] {
  if (period <= 0 || candles.length < period) {
    return []
  }
  const points: IndicatorPoint[] = []
  let sum = 0
  for (let index = 0; index < candles.length; index += 1) {
    sum += candles[index].close
    if (index >= period) {
      sum -= candles[index - period].close
    }
    if (index >= period - 1) {
      points.push({ time: candles[index].time, value: sum / period })
    }
  }
  return points
}

export type BollingerBands = {
  upper: IndicatorPoint[]
  middle: IndicatorPoint[]
  lower: IndicatorPoint[]
}

/* 표준편차는 모집단 기준이다. 백엔드가 쓰는 ta4j 와 같은 정의를 따른다. */
export function bollingerBands(
  candles: readonly Candle[],
  period = bollingerPeriod,
  multiplier = bollingerMultiplier,
): BollingerBands {
  const empty: BollingerBands = { upper: [], middle: [], lower: [] }
  if (period <= 0 || candles.length < period) {
    return empty
  }
  const bands: BollingerBands = { upper: [], middle: [], lower: [] }
  for (let index = period - 1; index < candles.length; index += 1) {
    let sum = 0
    for (let offset = index - period + 1; offset <= index; offset += 1) {
      sum += candles[offset].close
    }
    const mean = sum / period
    let variance = 0
    for (let offset = index - period + 1; offset <= index; offset += 1) {
      const deviation = candles[offset].close - mean
      variance += deviation * deviation
    }
    const spread = multiplier * Math.sqrt(variance / period)
    const time = candles[index].time
    bands.middle.push({ time, value: mean })
    bands.upper.push({ time, value: mean + spread })
    bands.lower.push({ time, value: mean - spread })
  }
  return bands
}

/*
 * RSI. 첫 평균은 단순 평균이고 그 뒤로는 Wilder 평활을 쓴다. 하락이 하나도 없는 구간은
 * 나눌 수 없어 100 으로 둔다.
 */
export function relativeStrengthIndex(candles: readonly Candle[], period = rsiPeriod): IndicatorPoint[] {
  if (period <= 0 || candles.length <= period) {
    return []
  }
  let gainSum = 0
  let lossSum = 0
  for (let index = 1; index <= period; index += 1) {
    const change = candles[index].close - candles[index - 1].close
    if (change > 0) gainSum += change
    else lossSum -= change
  }
  let averageGain = gainSum / period
  let averageLoss = lossSum / period

  const points: IndicatorPoint[] = [{ time: candles[period].time, value: toRsi(averageGain, averageLoss) }]
  for (let index = period + 1; index < candles.length; index += 1) {
    const change = candles[index].close - candles[index - 1].close
    const gain = change > 0 ? change : 0
    const loss = change < 0 ? -change : 0
    averageGain = (averageGain * (period - 1) + gain) / period
    averageLoss = (averageLoss * (period - 1) + loss) / period
    points.push({ time: candles[index].time, value: toRsi(averageGain, averageLoss) })
  }
  return points
}

function toRsi(averageGain: number, averageLoss: number): number {
  if (averageLoss === 0) {
    return averageGain === 0 ? 50 : 100
  }
  return 100 - 100 / (1 + averageGain / averageLoss)
}

export type VolumeBar = {
  time: number
  value: number
  /* 종가가 시가보다 높은 봉. 색 계약대로 빨강(--up)으로 그린다. */
  rising: boolean
}

export function volumeBars(candles: readonly Candle[]): VolumeBar[] {
  return candles.map((candle) => ({ time: candle.time, value: candle.volume, rising: candle.close >= candle.open }))
}

/* 범례 둘째 줄이 보여 줄 마지막 값. 값이 없으면 null 이다. */
export function lastValue(points: readonly IndicatorPoint[]): number | null {
  return points.at(-1)?.value ?? null
}

export type IndicatorData = {
  averages: IndicatorPoint[][]
  bands: BollingerBands
  volume: VolumeBar[]
  strength: IndicatorPoint[]
}

/* 한 번 계산해 차트와 범례가 함께 쓴다. 봉이 바뀔 때만 다시 만든다. */
export function computeIndicators(candles: readonly Candle[]): IndicatorData {
  return {
    averages: movingAveragePeriods.map((period) => movingAverage(candles, period)),
    bands: bollingerBands(candles),
    volume: volumeBars(candles),
    strength: relativeStrengthIndex(candles),
  }
}
