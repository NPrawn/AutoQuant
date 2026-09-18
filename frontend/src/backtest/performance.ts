import type { BacktestTrade, EquityPoint } from './types'

/*
 * 목업 12 의 성과 차트가 쓰는 계산. 첫 평가 시점을 100 으로 두어 전략과 단순보유를 같은 눈금에서
 * 견주고, 체결을 회차로 묶어 점을 찍는다. 화면은 여기서 나온 좌표와 문자열만 그린다.
 */
export const baseIndex = 100

/* 단순보유 비교 대상. 후보군에 늘 들어 있는 종목이라 벤치마크로 삼는다. */
export const benchmarkMarketCode = 'KRW-BTC'

export type IndexedPoint = { at: Date; value: number }
export type DrawdownPoint = { at: Date; rate: number }
export type BenchmarkCandle = { openTimeUnixMilli: number; closePrice: string }

export function indexEquity(points: readonly EquityPoint[]): IndexedPoint[] {
  const first = points[0]?.totalAssetValue
  if (first === undefined || first <= 0) {
    return []
  }
  /* 곱셈을 먼저 해 정수 원화끼리 나누면 90 이 90.000…1 로 흐르지 않는다. */
  return points.map((point) => ({ at: point.valuationAt, value: (point.totalAssetValue * baseIndex) / first }))
}

/* 낙폭은 잃은 비율이라 0 이상이다. 서버가 음수를 주더라도 0 으로 눌러 아래 구간이 위로 튀지 않게 한다. */
export function drawdownSeries(points: readonly EquityPoint[]): DrawdownPoint[] {
  return points.map((point) => ({ at: point.valuationAt, rate: Math.max(0, point.drawdownRate) }))
}

/*
 * 벤치마크 봉을 평가 시점에 맞춘다. 평가는 그 시각에 닫힌 봉까지로 이뤄지므로 시각보다 앞서
 * 시작한 마지막 봉의 종가를 쓴다. 같은 시각에 시작한 봉은 아직 열려 있어 쓰지 않는다.
 * 앞선 봉이 없는 시점은 건너뛴다. 처음 맞춰진 시점이 100 이다.
 */
export function indexBenchmark(candles: readonly BenchmarkCandle[], points: readonly EquityPoint[]): IndexedPoint[] {
  const closes = candles
    .map((candle) => ({ at: candle.openTimeUnixMilli, close: Number(candle.closePrice) }))
    .filter((candle) => Number.isFinite(candle.close) && candle.close > 0)
    .sort((left, right) => left.at - right.at)
  if (closes.length === 0) {
    return []
  }

  const aligned: IndexedPoint[] = []
  let cursor = 0
  let base: number | null = null
  for (const point of points) {
    const time = point.valuationAt.getTime()
    while (cursor + 1 < closes.length && closes[cursor + 1].at < time) {
      cursor += 1
    }
    if (closes[cursor].at >= time) {
      continue
    }
    base ??= closes[cursor].close
    aligned.push({ at: point.valuationAt, value: (closes[cursor].close * baseIndex) / base })
  }
  return aligned
}

/* KRW-BTC 를 BTC 로 읽는다. 카드 한 줄에 종목 다섯 개를 적어야 해서 짧게 쓴다. */
export function shortSymbol(marketCode: string): string {
  const dash = marketCode.indexOf('-')
  return dash === -1 ? marketCode : marketCode.slice(dash + 1)
}

export type RebalanceCycle = {
  /* 0 부터 시작. 화면은 1 을 더해 회차로 적는다. */
  index: number
  at: Date
  bought: string[]
  sold: string[]
  /* 직전 회차 대비 지수 변화율. 퍼센트 단위다. 첫 회차는 100 과 견준다. */
  returnRate: number
  equityIndex: number
}

/* 그 시각까지의 마지막 지수. 첫 평가 시점보다 앞서면 첫 값을 쓴다. */
export function indexAt(indexed: readonly IndexedPoint[], time: number): number {
  let value = indexed[0]?.value ?? baseIndex
  for (const point of indexed) {
    if (point.at.getTime() > time) {
      break
    }
    value = point.value
  }
  return value
}

/*
 * 같은 시각에 체결된 주문이 한 회차다. 다음 봉 시가 체결 규칙이라 한 회차의 체결은 시각이 같다.
 * 지수가 없으면 회차도 찍을 자리가 없어 비운다.
 */
export function rebalanceCycles(trades: readonly BacktestTrade[], indexed: readonly IndexedPoint[]): RebalanceCycle[] {
  if (indexed.length === 0) {
    return []
  }
  const groups = new Map<number, { bought: Set<string>; sold: Set<string> }>()
  for (const trade of trades) {
    const key = trade.filledAt.getTime()
    const group = groups.get(key) ?? { bought: new Set<string>(), sold: new Set<string>() }
    ;(trade.side === 'BUY' ? group.bought : group.sold).add(shortSymbol(trade.marketCode))
    groups.set(key, group)
  }

  let previous = baseIndex
  return [...groups.entries()]
    .sort(([left], [right]) => left - right)
    .map(([time, group], index) => {
      const equityIndex = indexAt(indexed, time)
      const cycle: RebalanceCycle = {
        index,
        at: new Date(time),
        bought: [...group.bought].sort(),
        sold: [...group.sold].sort(),
        returnRate: (equityIndex / previous - 1) * 100,
        equityIndex,
      }
      previous = equityIndex
      return cycle
    })
}

/* 목업의 viewBox 와 두 구간의 세로 자리. 위는 지수, 아래는 낙폭이다. */
export const chartFrame = {
  width: 1000,
  height: 440,
  left: 46,
  right: 946,
  equityTop: 16,
  equityBottom: 286,
  drawdownTop: 320,
  drawdownBottom: 410,
  axisLabelY: 428,
} as const

export type Scale = (value: number) => number

export function linearScale(lo: number, hi: number, outLo: number, outHi: number): Scale {
  const span = hi - lo
  if (span === 0) {
    return () => (outLo + outHi) / 2
  }
  return (value) => outLo + ((value - lo) / span) * (outHi - outLo)
}

export function timeScale(points: readonly { at: Date }[]): Scale {
  const start = points[0]?.at.getTime() ?? 0
  const end = points[points.length - 1]?.at.getTime() ?? start
  return linearScale(start, Math.max(end, start + 1), chartFrame.left, chartFrame.right)
}

/* 두 선이 다 들어가는 범위에 6% 여백을 둔다. 선이 테두리에 닿으면 끝값 라벨이 잘린다. */
export function equityDomain(series: readonly (readonly IndexedPoint[])[]): { lo: number; hi: number } {
  const values = series.flatMap((points) => points.map((point) => point.value))
  if (values.length === 0) {
    return { lo: baseIndex - 10, hi: baseIndex + 10 }
  }
  let lo = Math.min(...values)
  let hi = Math.max(...values)
  if (lo === hi) {
    lo -= 5
    hi += 5
  }
  const pad = (hi - lo) * 0.06
  return { lo: lo - pad, hi: hi + pad }
}

/* 낙폭 구간의 바닥. 낙폭이 거의 없어도 눈금 몇 개는 보이게 5% 는 확보한다. */
export function drawdownDomain(drawdown: readonly DrawdownPoint[]): number {
  const deepest = drawdown.reduce((max, point) => Math.max(max, point.rate), 0)
  return Math.max(0.05, deepest) * 1.12
}

/* 1·2·2.5·5 배수의 눈금. 값 범위가 어떻든 라벨이 대여섯 개로 읽힌다. */
export function niceTicks(lo: number, hi: number, count = 5): number[] {
  const span = hi - lo
  if (span <= 0) {
    return [lo]
  }
  const rough = span / count
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const candidates = [1, 2, 2.5, 5, 10].map((multiple) => multiple * magnitude)
  const step = candidates.find((candidate) => candidate >= rough) ?? candidates[candidates.length - 1]
  const ticks: number[] = []
  for (let value = Math.ceil(lo / step) * step; value <= hi + step * 1e-6; value += step) {
    ticks.push(Number(value.toFixed(10)))
  }
  return ticks
}

/* 월 첫날 눈금. 기간이 길면 라벨이 겹치니 최대 개수에 맞춰 건너뛴다. */
export function monthTicks(start: Date, end: Date, maxCount = 9): Date[] {
  const ticks: Date[] = []
  const cursor = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 1))
  while (cursor.getTime() <= end.getTime()) {
    ticks.push(new Date(cursor))
    cursor.setUTCMonth(cursor.getUTCMonth() + 1)
  }
  const every = Math.max(1, Math.ceil(ticks.length / maxCount))
  return ticks.filter((_, index) => index % every === 0)
}

export function deepestDrawdown(drawdown: readonly DrawdownPoint[]): DrawdownPoint | null {
  return drawdown.reduce<DrawdownPoint | null>(
    (deepest, point) => (deepest === null || point.rate > deepest.rate ? point : deepest),
    null,
  )
}

export type Coordinate = { x: number; y: number }

export function linePath(points: readonly Coordinate[]): string {
  return points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)},${point.y.toFixed(1)}`).join('')
}

/* 선 아래나 위를 한 면으로 닫는다. 클립과 채우기가 이 면을 쓴다. */
export function closedPath(points: readonly Coordinate[], baselineY: number): string {
  if (points.length === 0) {
    return ''
  }
  const first = points[0]
  const last = points[points.length - 1]
  return `${linePath(points)}L${last.x.toFixed(1)},${baselineY.toFixed(1)}L${first.x.toFixed(1)},${baselineY.toFixed(1)}Z`
}

export function formatIndex(value: number): string {
  return value.toFixed(1)
}

export function formatMonth(date: Date): string {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`
}
