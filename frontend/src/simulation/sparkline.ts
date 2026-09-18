import type { EquityPoint } from '../backtest/types'

/*
 * 목업 16 의 카드 안 수익 곡선. 축도 눈금도 없이 모양만 보이는 그림이라
 * 차트 라이브러리를 올리지 않고 좌표만 만들어 SVG 로 그린다.
 */

/* 목업 SVG 의 좌표계. 선 굵기는 vector-effect 로 고정돼 있어 그대로 늘려 써도 된다. */
export const sparkWidth = 600
export const sparkHeight = 140

/* 위아래가 붙지 않도록 남기는 여백. 값이 한 방향으로만 가도 선이 테두리에 닿지 않는다. */
const padding = 12

export type Spark = {
  /* 선 경로. 점이 둘 미만이면 만들지 않는다. */
  line: string
  /* 선 아래를 채우는 영역 경로. 그라데이션을 입힌다. */
  area: string
  /* 마지막 점. 지금 값을 가리킨다. */
  lastX: number
  lastY: number
  /* 구간 전체가 이익이면 상승, 아니면 하락이다. 색을 고르는 데 쓴다. */
  rising: boolean
}

export function sparkFromEquity(points: readonly EquityPoint[]): Spark | null {
  if (points.length < 2) {
    return null
  }

  const values = points.map((point) => point.totalAssetValue)
  const lowest = Math.min(...values)
  const highest = Math.max(...values)
  const span = highest - lowest

  const x = (index: number) => (index / (points.length - 1)) * sparkWidth
  /* 값이 모두 같으면 나눌 수 없다. 가운데 높이에 평평하게 둔다. */
  const y = (value: number) =>
    span === 0 ? sparkHeight / 2 : sparkHeight - padding - ((value - lowest) / span) * (sparkHeight - padding * 2)

  const coordinates = values.map((value, index) => `${round(x(index))} ${round(y(value))}`)
  const line = `M${coordinates.join('L')}`
  return {
    line,
    area: `${line}L${sparkWidth} ${sparkHeight}L0 ${sparkHeight}Z`,
    lastX: round(x(points.length - 1)),
    lastY: round(y(values[values.length - 1])),
    rising: values[values.length - 1] >= values[0],
  }
}

function round(value: number): number {
  return Math.round(value * 10) / 10
}
