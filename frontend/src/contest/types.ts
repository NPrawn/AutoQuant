/*
 * backend/api 의 대회 응답과 같은 모양이다 (docs/대회 기능 설계.md 6, 8절).
 * 시각만 Date 로 바꾸고 나머지는 응답 그대로 쓴다. 화면이 계산할 값이 없다.
 */

export type ContestDisplayStatus = 'SCHEDULED' | 'OPEN' | 'SCORING' | 'FINISHED'

export type MarketDatasetKind = 'GENERAL' | 'BULL' | 'BEAR' | 'SIDEWAYS'

export type ContestDisqualificationReason =
  | 'BACKTEST_FAILED'
  | 'RETURN_BELOW_LIMIT'
  | 'MDD_ABOVE_LIMIT'
  | 'LOW_EXPOSURE'

export type ContestSubmissionResponse = {
  strategyId: number
  strategyName: string
  submissionCount: number
  remainingSubmissions: number
  firstSubmittedAt: string
  lastSubmittedAt: string
}

export type ContestRoundItemResponse = {
  roundId: string
  title: string
  status: ContestDisplayStatus
  submissionStartsAt: string
  submissionEndsAt: string
  entryCount: number
  mySubmission: ContestSubmissionResponse | null
}

export type ContestRoundListResponse = {
  contests: ContestRoundItemResponse[]
}

export type ContestScoringRulesResponse = {
  returnWeight: number
  stabilityWeight: number
  minimumReturn: number
  maximumDrawdown: number
  minimumExposure: number
  initialCapital: number
  feeRate: number
  slippageBps: number
}

export type ContestRoundDetailResponse = {
  roundId: string
  title: string
  status: ContestDisplayStatus
  submissionStartsAt: string
  submissionEndsAt: string
  maxSubmissions: number
  warmupWeeks: number
  evaluationWeeks: number
  entryCount: number
  factorPeriodCaps: Record<string, number>
  scoringRules: ContestScoringRulesResponse
  mySubmission: ContestSubmissionResponse | null
}

export type SubmitContestEntryRequest = {
  strategyId: number
}

export type ContestEntryResponse = {
  roundId: string
  strategyId: number
  strategyName: string
  submissionCount: number
  remainingSubmissions: number
  firstSubmittedAt: string
  lastSubmittedAt: string
}

export type ContestProgressResponse = {
  status: ContestDisplayStatus
  finishedBacktests: number
  totalBacktests: number
}

export type ContestRankingItemResponse = {
  ranking: number | null
  nickname: string
  strategyName: string
  totalScore: number | null
  returnScore: number | null
  stabilityScore: number | null
  disqualificationReason: ContestDisqualificationReason | null
  disqualifiedKind: MarketDatasetKind | null
}

export type ContestResultsResponse = {
  rankings: ContestRankingItemResponse[]
}

export type ContestDatasetOutcomeResponse = {
  kind: MarketDatasetKind
  periodStart: string
  periodEnd: string
  marketReturn: number
  cumulativeReturn: number | null
  maximumDrawdown: number | null
  averageExposure: number | null
  tradeCount: number | null
}

export type ContestMyResultResponse = {
  ranking: number | null
  totalScore: number | null
  returnScore: number | null
  stabilityScore: number | null
  bullMddPercentile: number | null
  bearMddPercentile: number | null
  sidewaysMddPercentile: number | null
  disqualificationReason: ContestDisqualificationReason | null
  disqualifiedKind: MarketDatasetKind | null
  entryCount: number
  topTotalScore: number | null
  datasets: ContestDatasetOutcomeResponse[]
}

export type ContestDatasetItemResponse = {
  kind: MarketDatasetKind
  periodStart: string
  periodEnd: string
  evaluationReturn: number
  sourceWindowStart: string
  sourceWindowEnd: string
  seed: number
  generatorVersion: string
  filledHours: number
  sourceWeeks: string
}

export type ContestDatasetsResponse = {
  datasets: ContestDatasetItemResponse[]
}

/* 뽑은 주 목록은 생성기가 만든 JSON 문자열로 온다. 깨진 값이 와도 화면은 비워 두고 넘어간다. */
export type ContestSourceWeek = {
  sourceStart: string
  label: string
}

export function parseSourceWeeks(sourceWeeks: string): ContestSourceWeek[] {
  try {
    const parsed: unknown = JSON.parse(sourceWeeks)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .filter((week): week is { source_start: string; label: string } =>
        typeof week === 'object' && week !== null && 'source_start' in week && 'label' in week,
      )
      .map((week) => ({ sourceStart: week.source_start, label: week.label }))
  } catch {
    return []
  }
}
