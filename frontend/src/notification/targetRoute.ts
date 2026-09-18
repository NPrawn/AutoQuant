import { to } from '../app/routes.ts'
import type { AppNotification } from './types'

/*
 * 서버는 종류와 대상 식별자만 준다. 어디로 갈지는 화면이 정하고 그 대응은 여기 하나에 둔다.
 * 모르는 종류는 null 이다. 서버에 종류가 하나 늘어도 화면이 깨지지 않고 이동 버튼만 빠진다.
 */
export function targetRoute(notification: Pick<AppNotification, 'type' | 'targetId'>): string | null {
  if (notification.targetId === null) {
    return null
  }
  switch (notification.type) {
    case 'BACKTEST_COMPLETED':
    case 'BACKTEST_FAILED':
      return to.backtestRun(notification.targetId)
    case 'SIMULATION_FAILED':
      return to.simulationLive(notification.targetId)
    /* 대회 알림의 targetId 는 회차 식별자다. 제출 하나의 성적과 순위를 결과 화면에서 본다. */
    case 'CONTEST_SCORED':
      return to.contestResult(notification.targetId)
    default:
      return null
  }
}
