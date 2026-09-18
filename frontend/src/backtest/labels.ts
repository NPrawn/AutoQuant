import type { BacktestRunStatus, TradeSide } from './types'

/* 상태 문구와 칩 색. 목업 05 의 chip 변형을 그대로 쓴다. */
export const statusMeta: Record<BacktestRunStatus, { label: string; chip: string }> = {
  QUEUED: { label: '대기 중', chip: 'chip mute' },
  RUNNING: { label: '계산 중', chip: 'chip live' },
  COMPLETED: { label: '완료', chip: 'chip good' },
  FAILED: { label: '실패', chip: 'chip crit' },
}

/* 아직 끝나지 않은 실행. 이 동안만 조건 폼을 잠근다. 끝난 실행은 결과가 떠 있어도 폼을 막지 않는다. */
export function isRunInProgress(status: BacktestRunStatus): boolean {
  return status === 'QUEUED' || status === 'RUNNING'
}

export const sideLabels: Record<TradeSide, string> = { BUY: '매수', SELL: '매도' }

/* 아직 계산되지 않은 값의 표기. 목업의 빈 셀과 같다. */
export const blank = '—'

const krwFormat = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 })
const quantityFormat = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 8 })
const dateTimeFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
const dateFormat = new Intl.DateTimeFormat('sv-SE')

export function formatKrw(value: number | null): string {
  return value === null ? blank : krwFormat.format(value)
}

/*
 * 코인 가격. 금액이 작을수록 소수를 더 남긴다. 1원 미만 코인의 체결가·평균 매수가를 정수로 반올림하면
 * 0 이 찍혀 차트 화면의 표기와 어긋난다. 자릿수는 업비트 호가 단위를 대략 따른다.
 */
export function priceDecimals(value: number): number {
  const size = Math.abs(value)
  if (size >= 1000) return 0
  if (size >= 100) return 1
  if (size >= 10) return 2
  if (size >= 1) return 3
  return 4
}

export function formatPriceKrw(value: number | null): string {
  if (value === null) {
    return blank
  }
  const digits = priceDecimals(value)
  return value.toLocaleString('ko-KR', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

export function formatQuantity(value: number | null): string {
  return value === null ? blank : quantityFormat.format(value)
}

/* 비율을 퍼센트로 바꾼다. 부호가 의미 있는 값에만 signed 를 켠다. */
export function formatRate(value: number | null, options?: { signed?: boolean; digits?: number }): string {
  if (value === null) {
    return blank
  }
  const digits = options?.digits ?? 1
  const percent = value * 100
  const sign = options?.signed === true && percent > 0 ? '+' : ''
  return `${sign}${percent.toFixed(digits)}%`
}

/* 낙폭은 양수로 오지만 화면에서는 잃은 값이라 음수로 읽힌다. */
export function formatDrawdown(value: number | null): string {
  return value === null ? blank : `-${(Math.abs(value) * 100).toFixed(1)}%`
}

export function formatRatio(value: number | null): string {
  return value === null ? blank : value.toFixed(2)
}

export function formatDate(value: Date): string {
  return dateFormat.format(value)
}

export function formatDateTime(value: Date): string {
  return dateTimeFormat.format(value)
}

/* 상승과 하락에 색을 주는 클래스. 색 계약대로 빨강이 상승이다. */
export function trendClass(value: number | null): string {
  if (value === null || value === 0) {
    return ''
  }
  return value > 0 ? 'updown up' : 'updown down'
}
