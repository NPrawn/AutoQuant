/*
 * 백테스트 결과에서 모의투자 화면으로 넘어갈 때 어떤 전략인지 함께 보낸다.
 * strategy/backtestEntry 와 같은 방식이다. 값이 없거나 모양이 다르면 무시한다.
 */
export type SimulationEntryState = {
  strategyId: string
}

export function readSimulationEntry(state: unknown): SimulationEntryState | null {
  if (typeof state !== 'object' || state === null) {
    return null
  }
  const strategyId = (state as { strategyId?: unknown }).strategyId
  if (typeof strategyId !== 'string' || strategyId === '') {
    return null
  }
  return { strategyId }
}
