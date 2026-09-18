import type {
  ComparisonOperator,
  FactorType,
  Logic,
  Ranking,
  RankingDirection,
  RebalancePeriod,
  Timeframe,
} from './types'

/*
 * 열거형 값이 화면에 어떻게 읽히는지는 여기서만 정한다. 목업 19 의 문구를 그대로 옮겼다.
 * 컴포넌트가 '수익률' 같은 문자열을 직접 쓰면 문구를 바꿀 때 놓치는 곳이 생긴다.
 */
export type FactorMeta = {
  label: string
  short: string
  unit: string
  decimals: number
  defaultPeriod: number
}

export const factorMeta: Record<FactorType, FactorMeta> = {
  RSI: { label: 'RSI', short: 'RSI', unit: '', decimals: 1, defaultPeriod: 14 },
  RETURN: { label: 'N기간 수익률', short: '수익률', unit: '%', decimals: 1, defaultPeriod: 20 },
  SMA_DISPARITY: { label: 'SMA 이격률', short: 'SMA 이격률', unit: '%', decimals: 1, defaultPeriod: 20 },
  VOLATILITY: { label: '변동성', short: '변동성', unit: '%', decimals: 1, defaultPeriod: 20 },
  VOLUME_RATIO: { label: '거래량 비율', short: '거래량 비율', unit: '배', decimals: 2, defaultPeriod: 20 },
  DRAWDOWN: { label: '고점 대비 낙폭', short: '낙폭', unit: '%', decimals: 1, defaultPeriod: 60 },
}

export const factorTypes = Object.keys(factorMeta) as FactorType[]

export const operatorSymbols: Record<ComparisonOperator, string> = {
  GT: '>',
  GTE: '≥',
  LT: '<',
  LTE: '≤',
}

export const comparisonOperators = Object.keys(operatorSymbols) as ComparisonOperator[]

export const timeframeLabels: Record<Timeframe, string> = {
  '1H': '1시간봉',
  '4H': '4시간봉',
  '1D': '일봉',
}

export const timeframes = Object.keys(timeframeLabels) as Timeframe[]

export const directionLabels: Record<RankingDirection, string> = {
  DESC: '높은 순',
  ASC: '낮은 순',
}

export const logicLabels: Record<Logic, string> = {
  AND: 'AND',
  OR: 'OR',
}

/* 백엔드 RebalancePeriod 는 세 값뿐이다. 목업의 3일·2주는 계약에 없어 두지 않는다. */
export const rebalanceMeta: Record<RebalancePeriod, { label: string; note: string }> = {
  DAILY: { label: '매일', note: '종목 교체가 잦아 비용이 커집니다. 단기 모멘텀 전략에 씁니다.' },
  WEEKLY: { label: '매주', note: 'MVP 기본값입니다. 신호 반영과 거래비용의 균형이 가장 무난합니다.' },
  MONTHLY: { label: '매월', note: '회전율이 가장 낮습니다. 장기 팩터 전략에 맞습니다.' },
}

export const rebalancePeriods = Object.keys(rebalanceMeta) as RebalancePeriod[]

/* 수익률처럼 부호가 의미 있는 지표에만 + 를 붙인다. RSI, 변동성, 거래량 비율은 크기만 뜻해 부호를 붙이지 않는다. */
const unsignedFactors: ReadonlySet<FactorType> = new Set<FactorType>(['RSI', 'VOLATILITY', 'VOLUME_RATIO'])

export function formatFactorValue(factor: FactorType, value: number): string {
  const meta = factorMeta[factor]
  const signed = value > 0 && !unsignedFactors.has(factor) ? '+' : ''
  return `${signed}${value.toFixed(meta.decimals)}${meta.unit}`
}

export function describeCondition(factor: FactorType, operator: ComparisonOperator, value: number): string {
  return `${factorMeta[factor].short} ${operatorSymbols[operator]} ${formatFactorValue(factor, value)}`
}

export function describeRanking(ranking: Ranking): string {
  const arrow = ranking.direction === 'DESC' ? '↓' : '↑'
  return `${ranking.period}기간 ${factorMeta[ranking.factor].short} ${arrow}`
}
