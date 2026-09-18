import type { SimulationRunStatus } from './types'

/* 상태 문구와 칩 색. 정지는 오류가 아니라 경고 색으로 구분한다. */
export const statusMeta: Record<SimulationRunStatus, { label: string; chip: string }> = {
  CREATED: { label: '시작 전', chip: 'chip mute' },
  RUNNING: { label: '운용 중', chip: 'chip live' },
  STOPPED: { label: '정지됨', chip: 'chip warn' },
  FAILED: { label: '실패', chip: 'chip crit' },
}

/* 모르는 상태는 중립 칩이다. 서버가 상태를 하나 늘려도 목록과 상세가 깨지지 않는다. */
export function statusOf(status: string): { label: string; chip: string } {
  return (statusMeta as Record<string, { label: string; chip: string }>)[status] ?? { label: status, chip: 'chip mute' }
}

const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })

export function formatTime(value: Date): string {
  return timeFormat.format(value)
}
