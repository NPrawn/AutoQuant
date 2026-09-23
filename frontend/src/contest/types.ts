/*
 * backend/api 의 대회 응답과 같은 모양이다 (docs/대회 기능 설계.md 6, 8절).
 * 시각만 Date 로 바꾸고 나머지는 응답 그대로 쓴다. 화면이 계산할 값이 없다.
 */

export type ContestDisplayStatus = 'SCHEDULED' | 'OPEN' | 'SCORING' | 'FINISHED'

export type MarketDatasetKind = 'GENERAL' | 'BULL' | 'BEAR' | 'SIDEWAYS'

/* 크게 잃거나 깊이 빠진 전략은 실격이 아니라 점수가 내려간다. 수익률 점수는 음수까지 간다 (ADR-0003). */
export type ContestDisqualificationReason = 'BACKTEST_FAILED' | 'LOW_EXPOSURE'

/* 내 마지막 제출이다. 제출마다 행이 쌓이므로 그 앞의 제출은 순위 목록에 있다. */
export type ContestSubmissionResponse = {
  strategyId: number
  strategyName: string
  submittedAt: string
}

export type ContestRoundItemResponse = {
  roundId: string
  title: string
  status: ContestDisplayStatus
  submissionStartsAt: string
  submissionEndsAt: string
  entryCount: number
  mySubmission: ContestSubmissionResponse | null
  /* 제출하지 않았거나 채점 전이거나 실격이면 비어 있다. 마감 전 순위는 잠정이다. */
  myRanking: number | null
  myTotalScore: number | null
  /* 채점된 제출이 하나도 없으면 비어 있다. */
  topStrategyName: string | null
  topNickname: string | null
  topTotalScore: number | null
}

export type ContestRoundListResponse = {
  contests: ContestRoundItemResponse[]
}

export type ContestScoringRulesResponse = {
  returnWeight: number
  stabilityWeight: number
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
  submittedAt: string
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
  /* 균등 보유의 최대 낙폭을 내 전략 봉 간격으로 잰 값. 국면 점수의 기준이다. */
  marketMaximumDrawdown: number
  cumulativeReturn: number | null
  maximumDrawdown: number | null
  averageExposure: number | null
  tradeCount: number | null
  /* 하루 한 점 누적 수익률. 마감 전에는 빈 목록이다 (ADR-0010). */
  equityCurve: ContestEquityPointResponse[]
}

/*
 * 점수도 실격 사유도 없으면 아직 채점 전이다. 백테스트 4벌이 끝나도 채점 스케줄러가 점수를 쓰기 전까지는
 * 이 상태라, 끝난 백테스트 수만 보고 기다리기를 멈추면 화면이 채점 중에 머문다.
 */
export function awaitingScore(result: Pick<ContestMyResultResponse, 'totalScore' | 'disqualificationReason'>): boolean {
  return result.totalScore === null && result.disqualificationReason === null
}

export type ContestEquityPointResponse = {
  valuationAt: string
  returnRate: number
}

export type ContestMyResultResponse = {
  strategyId: number
  strategyName: string
  /* 백테스트가 도는 동안에는 점수가 비어 있고 진행 수만 온다. */
  finishedBacktests: number
  totalBacktests: number
  ranking: number | null
  totalScore: number | null
  returnScore: number | null
  stabilityScore: number | null
  bullRegimeScore: number | null
  bearRegimeScore: number | null
  sidewaysRegimeScore: number | null
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

/* 랭킹은 순위가 매겨진 제출만 본다. 실격 제출은 목록에서 빠지고 수만 따로 온다 (S15P21A107-233). */
export type ContestRankingSort = 'TOTAL' | 'STABILITY'

/* 국면 강점 필터. 일반장은 세 국면과 견주는 값이 아니라 필터 대상이 아니다. */
export type ContestRegimeKind = Exclude<MarketDatasetKind, 'GENERAL'>

export type ContestRankingRowResponse = {
  ranking: number
  strategyId: number
  strategyName: string
  nickname: string
  published: boolean
  totalScore: number
  returnScore: number
  stabilityScore: number
  bullRegimeScore: number
  bearRegimeScore: number
  sidewaysRegimeScore: number
  mine: boolean
}

export type ContestRankingPageResponse = {
  content: ContestRankingRowResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  disqualifiedCount: number
}
