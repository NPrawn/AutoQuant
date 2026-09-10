import type { BacktestRun, BacktestRunStatus } from './types'

/*
 * 실행 이력을 고르는 순수 함수. 목록 API 는 전략별 필터가 없어 화면이 거른다.
 * 서버 필터가 생기면 이 파일의 호출처만 바뀐다.
 */
export function runsForStrategy(runs: BacktestRun[], strategyId: string): BacktestRun[] {
  return runs.filter((run) => run.strategyId === strategyId)
}

/* 완료 시각이 가장 늦은 실행. 완료 시각이 없는 완료 응답은 접수 시각으로 대신 잰다. */
export function latestCompletedRun(runs: BacktestRun[]): BacktestRun | null {
  const finishedAt = (run: BacktestRun): number => (run.completedAt ?? run.queuedAt).getTime()
  return runs
    .filter((run) => run.status === 'COMPLETED')
    .reduce<BacktestRun | null>((latest, run) => (latest === null || finishedAt(run) > finishedAt(latest) ? run : latest), null)
}

/* 응답과 화면 모델 어느 쪽에도 쓸 수 있게 상태만 본다. */
export function hasUnsettledRun(runs: ReadonlyArray<{ status: BacktestRunStatus }>): boolean {
  return runs.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING')
}
