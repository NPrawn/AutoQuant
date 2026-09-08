/*
 * 전략 목록·상세에서 백테스트 화면으로 넘어갈 때 어떤 전략인지 함께 보낸다.
 * 백테스트 화면 작업이 이 값을 읽어 전략을 미리 선택한다. builderEntry 와 같은 방식이다.
 */
export type BacktestEntryState = {
  strategyId: string
}

export function readBacktestEntry(state: unknown): BacktestEntryState | null {
  if (typeof state !== 'object' || state === null) {
    return null
  }
  const strategyId = (state as { strategyId?: unknown }).strategyId
  if (typeof strategyId !== 'string' || strategyId === '') {
    return null
  }
  return { strategyId }
}
