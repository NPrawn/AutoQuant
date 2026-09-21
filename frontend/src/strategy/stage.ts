import type { BacktestRun } from '../backtest/types.ts'
import type { SimulationRun } from '../simulation/types.ts'

/*
 * 전략이 지금 어느 단계인지. 상세의 주 버튼과 목록의 상태 칩이 같은 판정을 쓴다.
 * 운용 중 모의투자가 있으면 운용 중, 대회에 냈으면 제출, 완료된 백테스트가 있으면 검증됨, 아니면 초안이다.
 * 운용 중이 제출보다 앞서는 이유는 지금 돌아가는 것이 사용자가 먼저 볼 일이라서다.
 */
export type StrategyStage = 'draft' | 'verified' | 'running' | 'submitted'

export type StageInput = {
  runs: readonly BacktestRun[]
  simulations: readonly SimulationRun[]
  submitted: boolean
}

export type PrimaryAction =
  | { kind: 'backtest'; label: '백테스트 실행' }
  | { kind: 'simulate'; label: '모의투자 시작' }
  | { kind: 'live'; label: '실시간 보기'; simulationId: string }

export function simulationsForStrategy(runs: readonly SimulationRun[], strategyId: string): SimulationRun[] {
  return simulationsForVersions(runs, [strategyId])
}

/* 한 전략의 모든 판으로 돌린 모의투자. 판마다 전략 식별자가 다르므로 묶음으로 거른다. */
export function simulationsForVersions(runs: readonly SimulationRun[], strategyIds: readonly string[]): SimulationRun[] {
  const ids = new Set(strategyIds)
  return runs.filter((run) => ids.has(run.strategyId)).sort(byRecent)
}

/* 시작한 것이 최근인 순. 시작 전(CREATED) 은 맨 위다. */
function byRecent(left: SimulationRun, right: SimulationRun): number {
  const l = left.startedAt?.getTime() ?? Number.POSITIVE_INFINITY
  const r = right.startedAt?.getTime() ?? Number.POSITIVE_INFINITY
  return r - l
}

export function runningSimulations(simulations: readonly SimulationRun[]): SimulationRun[] {
  return simulations.filter((run) => run.status === 'RUNNING')
}

export function strategyStage({ runs, simulations, submitted }: StageInput): StrategyStage {
  if (runningSimulations(simulations).length > 0) {
    return 'running'
  }
  if (submitted) {
    return 'submitted'
  }
  if (runs.some((run) => run.status === 'COMPLETED')) {
    return 'verified'
  }
  return 'draft'
}

export const stageMeta: Record<StrategyStage, { label: string; chip: string }> = {
  draft: { label: '초안', chip: 'chip mute' },
  verified: { label: '검증됨', chip: 'chip good' },
  running: { label: '운용 중', chip: 'chip live' },
  submitted: { label: '대회 제출', chip: 'chip good' },
}

/* 단계마다 다음 행동은 하나다. 운용 중이면 가장 최근에 시작한 실행으로 간다. */
export function primaryAction(input: StageInput): PrimaryAction {
  const stage = strategyStage(input)
  if (stage === 'running') {
    const live = runningSimulations(input.simulations).sort(byRecent)[0]
    return { kind: 'live', label: '실시간 보기', simulationId: live.id }
  }
  if (stage === 'draft') {
    return { kind: 'backtest', label: '백테스트 실행' }
  }
  return { kind: 'simulate', label: '모의투자 시작' }
}
