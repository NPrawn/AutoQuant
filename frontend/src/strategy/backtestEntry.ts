/*
 * 전략 목록·상세에서 백테스트 화면으로 넘어갈 때 어떤 전략인지 함께 보낸다.
 * 백테스트 화면 작업이 이 값을 읽어 전략을 미리 선택한다. builderEntry 와 같은 방식이다.
 * 기간과 초기자산이 있으면 "같은 조건 다시" 다. 폼을 그 값으로 채우고 기본 기간을 덮지 않는다.
 */
export type BacktestEntryState = {
  strategyId: string
  /* YYYY-MM-DD. 둘 다 있을 때만 쓴다. */
  periodStart?: string
  periodEnd?: string
  initialCapital?: number
}

const isDateInput = (value: unknown): value is string => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)

export function readBacktestEntry(state: unknown): BacktestEntryState | null {
  if (typeof state !== 'object' || state === null) {
    return null
  }
  const raw = state as { strategyId?: unknown; periodStart?: unknown; periodEnd?: unknown; initialCapital?: unknown }
  if (typeof raw.strategyId !== 'string' || raw.strategyId === '') {
    return null
  }
  const entry: BacktestEntryState = { strategyId: raw.strategyId }
  if (isDateInput(raw.periodStart) && isDateInput(raw.periodEnd)) {
    entry.periodStart = raw.periodStart
    entry.periodEnd = raw.periodEnd
  }
  if (typeof raw.initialCapital === 'number' && Number.isFinite(raw.initialCapital) && raw.initialCapital > 0) {
    entry.initialCapital = raw.initialCapital
  }
  return entry
}

/* 진입 상태가 기간을 들고 왔는지. 그러면 첫 렌더의 기본 기간 프리셋을 적용하지 않는다. */
export function hasPeriod(entry: BacktestEntryState | null): entry is BacktestEntryState & { periodStart: string; periodEnd: string } {
  return entry !== null && entry.periodStart !== undefined && entry.periodEnd !== undefined
}
