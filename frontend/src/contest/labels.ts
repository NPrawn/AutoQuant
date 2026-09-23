import type {
  ContestDisplayStatus,
  ContestDisqualificationReason,
  ContestRankingSort,
  ContestRegimeKind,
  MarketDatasetKind,
} from './types'

export const contestStatusLabels: Record<ContestDisplayStatus, string> = {
  SCHEDULED: '예정',
  OPEN: '제출 중',
  SCORING: '채점 중',
  FINISHED: '종료',
}

export const datasetKindLabels: Record<MarketDatasetKind, string> = {
  GENERAL: '일반장',
  BULL: '상승장',
  BEAR: '하락장',
  SIDEWAYS: '횡보장',
}

const disqualificationLabels: Record<ContestDisqualificationReason, string> = {
  BACKTEST_FAILED: '백테스트 실패',
  LOW_EXPOSURE: '평균 투자 비중 미달',
}

/* 실격 사유는 어떤 데이터셋에서 걸렸는지까지 보여 줘야 참가자가 다음 제출을 고칠 수 있다. */
export function describeDisqualification(
  reason: ContestDisqualificationReason | null,
  kind: MarketDatasetKind | null,
): string | null {
  if (reason === null) {
    return null
  }
  const label = disqualificationLabels[reason]
  return kind === null ? label : `${datasetKindLabels[kind]} ${label}`
}

export function formatPercent(value: number | null, fractionDigits = 1): string {
  if (value === null || Number.isNaN(value)) {
    return '—'
  }
  return `${(value * 100).toFixed(fractionDigits)}%`
}

export function formatScore(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return '—'
  }
  return value.toFixed(1)
}

/* 종합 점수는 음수가 흔해 부호를 늘 붙인다. 색이 없는 표에서도 플러스와 마이너스가 갈린다. */
export function formatSignedScore(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return '—'
  }
  const fixed = value.toFixed(1)
  return value > 0 ? `+${fixed}` : fixed
}

/* 종합 점수의 등락색 클래스. 수익률 컬럼이 없는 표에서만 쓴다. 옆에 수익률이 있으면 색이 두 겹이라 흰색으로 둔다. */
export function scoreTone(value: number | null): string {
  if (value === null || Number.isNaN(value) || value === 0) {
    return 'mono score-lead'
  }
  return value < 0 ? 'mono score-lead updown down' : 'mono score-lead updown up'
}

/* 1, 2, 3위는 금, 은, 동. 그 아래는 회색 순번 그대로다. */
export function rankTone(ranking: number | null): string {
  switch (ranking) {
    case 1:
      return 'rnum gold'
    case 2:
      return 'rnum silver'
    case 3:
      return 'rnum bronze'
    default:
      return 'rnum'
  }
}

export function formatDateTime(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return '—'
  }
  return parsed.toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })
}

export function formatDate(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return '—'
  }
  return parsed.toLocaleDateString('ko-KR', { dateStyle: 'medium' })
}

/*
 * 마감까지 남은 시간. 목업 30·31 의 시계 자리에 들어간다. 분 단위 아래는 보이지 않으므로
 * 초는 버린다. 이미 지났으면 남은 시간이 없다는 뜻으로 null 을 준다.
 */
export function formatRemaining(endsAt: string, now: number): string | null {
  const end = new Date(endsAt).getTime()
  if (Number.isNaN(end) || end <= now) {
    return null
  }
  const minutes = Math.floor((end - now) / 60_000)
  const days = Math.floor(minutes / (60 * 24))
  const hours = Math.floor((minutes % (60 * 24)) / 60)
  if (days > 0) {
    return `${days}일 ${hours}시간`
  }
  if (hours > 0) {
    return `${hours}시간 ${minutes % 60}분`
  }
  return `${minutes}분`
}

/* 채점 진행률. 백테스트가 하나도 없으면 0%로 본다. */
export function progressRatio(finished: number, total: number): number {
  if (total <= 0) {
    return 0
  }
  return Math.min(Math.max(finished / total, 0), 1)
}

export const rankingSortLabels: Record<ContestRankingSort, string> = {
  TOTAL: '종합 점수',
  STABILITY: '안정성',
}

/* 국면 강점 필터의 선택지. 일반장은 세 국면과 견주는 값이 아니라 빠진다. */
export const regimeKinds: ContestRegimeKind[] = ['BULL', 'BEAR', 'SIDEWAYS']


/* 목업의 두 자리 순위 표기. 10위 아래는 앞에 0을 붙인다. */
export function formatRank(ranking: number): string {
  return ranking < 10 ? `0${ranking}` : String(ranking)
}
