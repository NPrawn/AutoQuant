/*
 * 대회 결과 화면의 그림 좌표를 계산한다. 목업 33 의 장세 카드 곡선(.regime-spark)과
 * 비교 막대(.pct-track)가 쓴다. 화면 컴포넌트는 여기서 받은 숫자만 그린다.
 */

export type SparkPaths = {
  line: string
  area: string
}

/* 목업 곡선의 viewBox(200 × 46)와 안쪽 여백. */
const sparkWidth = 200
const sparkHeight = 46
const sparkPadding = 4

/*
 * 누적 수익률 목록을 선과 면 경로로 바꾼다. 곡선은 평가 시작(수익률 0)에서 출발한다.
 * 값이 모두 같으면 가운데에 평평한 선을 긋는다.
 */
export function sparkPaths(returnRates: readonly number[]): SparkPaths | null {
  if (returnRates.length === 0) {
    return null
  }
  const values = [0, ...returnRates]
  const low = Math.min(...values)
  const high = Math.max(...values)
  const innerWidth = sparkWidth - sparkPadding * 2
  const innerHeight = sparkHeight - sparkPadding * 2
  const bottom = sparkHeight - sparkPadding

  const points = values.map((value, index) => {
    const x = sparkPadding + (innerWidth * index) / (values.length - 1)
    const y = high === low ? sparkHeight / 2 : bottom - ((value - low) / (high - low)) * innerHeight
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  const line = `M${points.join('L')}`
  const area = `${line}L${sparkWidth - sparkPadding},${sparkHeight}L${sparkPadding},${sparkHeight}Z`
  return { line, area }
}

export type ComparisonAxis = {
  low: number
  high: number
}

/* 한 카드 안의 내 값과 시장 값을 모두 담는 축. 막대가 0에서 출발하므로 0을 꼭 넣는다. */
export function comparisonAxis(values: readonly (number | null)[]): ComparisonAxis {
  const known = values.filter((value): value is number => value !== null && !Number.isNaN(value))
  const low = Math.min(0, ...known)
  const high = Math.max(0, ...known)
  return high === low ? { low, high: low + 1 } : { low, high }
}

/* 축 위의 위치를 0~100(%)으로 옮긴다. */
export function axisPercent(value: number, axis: ComparisonAxis): number {
  const percent = ((value - axis.low) / (axis.high - axis.low)) * 100
  return Math.min(Math.max(percent, 0), 100)
}

/* 0에서 값까지 이어지는 막대. 음수면 0의 왼쪽으로 뻗는다. */
export function barSpan(value: number, axis: ComparisonAxis): { left: number; width: number } {
  const zero = axisPercent(0, axis)
  const end = axisPercent(value, axis)
  return { left: Math.min(zero, end), width: Math.abs(end - zero) }
}
