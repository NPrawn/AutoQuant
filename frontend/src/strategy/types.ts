/*
 * backend/api 의 strategy.domain.dsl 과 같은 모양. JSON 판별자는 Jackson 설정대로 `type` 이다.
 * AI 서버 응답도 Spring 을 거쳐 같은 모양으로 온다. 이 파일은 계약만 두고 편집 상태는 draft.ts 가 맡는다.
 */
export type Timeframe = '1H' | '4H' | '1D'

export type FactorType = 'RETURN' | 'RSI' | 'SMA_DISPARITY' | 'VOLATILITY' | 'VOLUME_RATIO' | 'DRAWDOWN'

export type ComparisonOperator = 'GT' | 'GTE' | 'LT' | 'LTE'

export type Logic = 'AND' | 'OR'

export type RankingDirection = 'ASC' | 'DESC'

export type RebalancePeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY'

export type FactorOperand = {
  type: 'FACTOR'
  factor: FactorType
  period: number
}

export type ValueOperand = {
  type: 'VALUE'
  value: number
}

export type Operand = FactorOperand | ValueOperand

export type ConditionFilterNode = {
  type: 'CONDITION'
  left: Operand
  operator: ComparisonOperator
  right: Operand
}

export type GroupFilterNode = {
  type: 'GROUP'
  logic: Logic
  children: FilterNode[]
}

export type FilterNode = GroupFilterNode | ConditionFilterNode

export type Ranking = {
  factor: FactorType
  period: number
  direction: RankingDirection
}

export type StrategyDsl = {
  timeframe: Timeframe
  filters: FilterNode
  ranking: Ranking
  selection: { topN: number }
  rebalance: { period: RebalancePeriod }
}

/* --- /strategies --- */

export type StrategyResponse = {
  strategyId: number
  name: string
  description: string | null
  strategyDsl: StrategyDsl
  createdAt: string
}

export type CreateStrategyRequest = {
  name: string
  description?: string
  strategyDsl: StrategyDsl
}

/* 화면이 쓰는 전략 모델. 식별자는 라우트 파라미터와 맞추기 위해 문자열로 둔다. */
export type Strategy = {
  id: string
  name: string
  description: string
  dsl: StrategyDsl
  createdAt: Date
}

export function toStrategy(response: StrategyResponse): Strategy {
  return {
    id: String(response.strategyId),
    name: response.name,
    description: response.description ?? '',
    dsl: response.strategyDsl,
    createdAt: new Date(response.createdAt),
  }
}

/* --- /ai/strategies/translate --- */

export type TranslateStrategyRequest = {
  userInput: string
  currentStrategy?: StrategyDsl
}

export type TranslateStrategyResponse = {
  strategy: StrategyDsl
  meta: {
    attempts: number
    elapsedMs: number
    warnings: string[]
    assumptions: string[]
    changes: string[]
  }
}

/* 백엔드 검증 상한. 화면 입력에도 같은 값을 쓴다. */
export const strategyNameMaxLength = 100
export const translateInputMaxLength = 500
