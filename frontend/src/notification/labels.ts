import type { NotificationType } from './types'

/*
 * 알림 한 줄의 겉모습. 목업 37 의 .notif 변형(alert·done·contest)과 아이콘 글자를 정한다.
 * 색 계약을 지켜 실패만 crit 을 쓰는 alert 로 두고 완료는 good 을 쓰는 done 으로 둔다.
 */
type NotificationLook = {
  tone: 'alert' | 'done' | 'contest'
  icon: string
  action: string
}

export const notificationLooks: Record<NotificationType, NotificationLook> = {
  BACKTEST_COMPLETED: { tone: 'done', icon: '✓', action: '결과 보기' },
  BACKTEST_FAILED: { tone: 'alert', icon: '!', action: '사유 보기' },
  SIMULATION_FAILED: { tone: 'alert', icon: '!', action: '상태 보기' },
  /* 대회 채점은 제출 하나에 하나만 온다. 목업 37 의 대회 줄과 같은 색을 쓴다. */
  CONTEST_SCORED: { tone: 'contest', icon: '★', action: '결과 보기' },
}

/* 서버에 종류가 하나 늘어도 화면이 깨지지 않게 모르는 종류는 중립 모양이다. */
const unknownLook: NotificationLook = { tone: 'done', icon: '•', action: '보기' }

export function notificationLook(type: string): NotificationLook {
  return (notificationLooks as Record<string, NotificationLook>)[type] ?? unknownLook
}

const dayInMs = 24 * 60 * 60 * 1000

/*
 * 목업의 시각 표기. 오늘은 시:분, 어제는 "어제", 그 앞은 "N일 전"이다.
 * 날짜 경계는 로컬 자정 기준이라 새벽 1시의 알림도 날이 바뀌면 "어제"가 된다.
 */
export function notificationTime(createdAt: Date, now: Date): string {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const createdTime = createdAt.getTime()

  if (createdTime >= startOfToday) {
    return `${String(createdAt.getHours()).padStart(2, '0')}:${String(createdAt.getMinutes()).padStart(2, '0')}`
  }

  const daysAgo = Math.ceil((startOfToday - createdTime) / dayInMs)
  if (daysAgo === 1) {
    return '어제'
  }
  if (daysAgo < 7) {
    return `${daysAgo}일 전`
  }
  return `${createdAt.getFullYear()}-${pad(createdAt.getMonth() + 1)}-${pad(createdAt.getDate())}`
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}
