import type { FilterNode, Operand, StrategyDsl, Timeframe } from '../strategy/types'

/*
 * 백테스트가 고를 수 있는 기간의 경계. 엔진은 평가 시작일 "이전"에 워밍업 봉을 읽어 팩터를
 * 계산하므로 캔들 보유 시작일을 그대로 시작일로 쓰면 그 구간이 비어 실행이 실패한다.
 * 그래서 하한은 보유 시작일이 아니라 보유 시작일에 워밍업 구간을 더한 날이다.
 */
export type CandleCoverage = {
  start: string
  end: string
}

/* 봉 하나의 길이. 백엔드 Timeframe 열거형과 같은 값이다. */
export const timeframeIntervalMinutes: Record<Timeframe, number> = { '1H': 60, '4H': 240, '1D': 1440 }

/* 캔들 보유 시작일을 아직 모를 때 쓰는 하한. 업비트 KRW 상장 초기 구간이다. */
export const fallbackPeriodStart = '2018-01-01'

const minutesPerDay = 60 * 24

export function toDateInput(value: Date): string {
  return new Date(value.getTime() - value.getTimezoneOffset() * 60 * 1000).toISOString().slice(0, 10)
}

/* 여러 종목의 봉 시각을 모아 첫 날과 마지막 날을 잰다. 봉이 없으면 null 이다. */
export function coverageFromOpenTimes(openTimesUnixMilli: readonly number[]): CandleCoverage | null {
  if (openTimesUnixMilli.length === 0) {
    return null
  }
  let min = openTimesUnixMilli[0]
  let max = openTimesUnixMilli[0]
  for (const time of openTimesUnixMilli) {
    if (time < min) min = time
    if (time > max) max = time
  }
  return { start: toDateInput(new Date(min)), end: toDateInput(new Date(max)) }
}

/*
 * 전략이 요구하는 워밍업 봉 수. 백엔드 BacktestWarmupCandleCountResolver 와 같은 규칙이라
 * 한쪽 규칙이 바뀌면 다른 쪽도 함께 바꿔야 한다.
 */
export function warmupCandleCount(dsl: StrategyDsl): number {
  return Math.max(dsl.ranking.period, filterMaximumPeriod(dsl.filters)) + 1
}

function filterMaximumPeriod(node: FilterNode): number {
  if (node.type === 'GROUP') {
    return node.children.reduce((longest, child) => Math.max(longest, filterMaximumPeriod(child)), 0)
  }
  return Math.max(operandPeriod(node.left), operandPeriod(node.right))
}

function operandPeriod(operand: Operand): number {
  return operand.type === 'FACTOR' ? operand.period : 0
}

/* 날짜를 봉 개수만큼 뒤로 민다. 날짜 입력은 하루 단위라 하루 미만은 하루로 올린다. */
export function shiftByCandles(date: string, candles: number, timeframe: Timeframe): string {
  const days = Math.ceil((candles * timeframeIntervalMinutes[timeframe]) / minutesPerDay)
  const shifted = new Date(`${date}T00:00:00.000`)
  shifted.setDate(shifted.getDate() + days)
  return toDateInput(shifted)
}

export type PeriodBounds = {
  /* 고를 수 있는 가장 이른 시작일. 프리셋과 검증이 함께 쓴다. */
  earliestStart: string
  /* 캔들 보유 구간. 아직 읽지 못했으면 null 이고 안내 문구에 쓴다. */
  coverage: CandleCoverage | null
  /* 전략이 요구하는 워밍업 봉 수. 전략을 고르기 전에는 0 이다. */
  warmupCandles: number
  timeframe: Timeframe | null
  /* 워밍업을 빼고 나면 평가할 구간이 남지 않는다. 실행해도 결과가 나오지 않는다. */
  tooShort: boolean
}

/*
 * 보유 구간과 전략에서 기간 경계를 만든다. 전략을 아직 고르지 않았으면 워밍업을 모르므로
 * 보유 시작일을 그대로 하한으로 둔다. 전략이 정해지면 그만큼 뒤로 밀린다.
 */
export function periodBounds(coverage: CandleCoverage | null, dsl: StrategyDsl | null, today: Date): PeriodBounds {
  const coverageStart = coverage?.start ?? fallbackPeriodStart
  const warmupCandles = dsl === null ? 0 : warmupCandleCount(dsl)
  const timeframe = dsl?.timeframe ?? null
  const earliestStart =
    dsl === null || coverage === null ? coverageStart : shiftByCandles(coverageStart, warmupCandles, dsl.timeframe)

  return {
    earliestStart,
    coverage,
    warmupCandles,
    timeframe,
    tooShort: earliestStart >= toDateInput(today),
  }
}
