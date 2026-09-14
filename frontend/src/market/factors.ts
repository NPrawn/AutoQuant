import { factorMeta, formatFactorValue } from '../strategy/labels.ts'
import type { FactorType, Timeframe } from '../strategy/types'
import type { CandleInterval } from './model'
import { changeClass } from './ticker.ts'
import type { AssetFactorsResponse } from './types'

/*
 * 팩터 값은 서버가 계산해 주고 여기서는 표기만 만든다. 화면이 다시 계산하면
 * 사용자가 보고 조건을 정한 숫자와 전략이 실제로 쓰는 숫자가 어긋난다.
 */

/* 팩터는 1시간, 4시간, 일봉으로만 계산한다. 그보다 짧은 간격을 보고 있으면 일봉 값을 보인다. */
export function factorTimeframe(interval: CandleInterval): Timeframe {
  if (interval === 60) {
    return '1H'
  }
  if (interval === 240) {
    return '4H'
  }
  return '1D'
}

export type FactorRow = {
  factor: FactorType
  /* 빌더와 같은 표기를 쓴다. 기간은 서버가 정해 응답에 실려 온다. */
  label: string
  text: string
  tone: string
}

/* 부호가 방향을 뜻하는 팩터에만 색을 준다. RSI, 변동성, 거래량 비율은 크기만 뜻한다. */
const signedFactors: ReadonlySet<FactorType> = new Set<FactorType>(['RETURN', 'SMA_DISPARITY', 'DRAWDOWN'])

export function factorRows(response: AssetFactorsResponse | undefined): FactorRow[] {
  if (response === undefined) {
    return []
  }
  return response.factors.map((entry) => ({
    factor: entry.factor,
    label: `${factorMeta[entry.factor].short} ${entry.period}`,
    text: entry.value === null ? '—' : formatFactorValue(entry.factor, entry.value),
    tone: entry.value === null || !signedFactors.has(entry.factor) ? '' : changeClass(entry.value),
  }))
}

/* 값이 빈 팩터가 있으면 왜 없는지 한 줄로 알린다. 오류가 아니라 봉이 모자란 것이다. */
export function missingFactorNote(response: AssetFactorsResponse | undefined): string | null {
  const missing = (response?.factors ?? []).filter((entry) => entry.value === null)
  if (missing.length === 0) {
    return null
  }
  const names = missing.map((entry) => `${factorMeta[entry.factor].short} ${entry.period}`).join(', ')
  return `봉이 기간을 채우지 못해 아직 값이 없습니다: ${names}`
}
