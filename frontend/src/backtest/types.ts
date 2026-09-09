/* backend/api 의 trading.backtest 응답과 같은 모양. BigDecimal 은 Jackson 기본 설정대로 JSON 숫자로 온다. */
export type BacktestRunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export type ExecutionPriceRule = 'NEXT_CANDLE_OPEN'

export type TradeSide = 'BUY' | 'SELL'

export type PositionSide = 'LONG' | 'SHORT'

export type BacktestRunCreatedResponse = {
  backtestId: string
  status: BacktestRunStatus
  queuedAt: string
}

export type BacktestRunResponse = {
  backtestId: string
  strategyId: number
  status: BacktestRunStatus
  periodStart: string
  periodEnd: string
  timeframe: string
  quoteCurrency: string
  initialCapital: number
  feeRate: number
  slippageBps: number
  rebalanceInterval: string
  executionPriceRule: ExecutionPriceRule
  engineVersion: string
  marketDataVersion: string
  queuedAt: string
  startedAt: string | null
  completedAt: string | null
}

/* 수익률과 낙폭은 비율이다. cumulativeReturn 0.589 는 58.9% 이고 mdd 는 양수로 온다. */
export type BacktestResultResponse = {
  backtestId: string
  finalAssetValue: number
  cumulativeReturn: number
  cagr: number | null
  mdd: number
  sharpeRatio: number | null
  volatility: number | null
  winRate: number | null
  tradeCount: number
  totalFee: number
  benchmarkReturn: number | null
}

export type BacktestTradeResponse = {
  tradeId: string
  assetId: string
  assetMarketCode: string
  positionSide: PositionSide
  side: TradeSide
  signalAt: string
  filledAt: string
  executedPrice: number
  quantity: number
  notional: number
  feeAmount: number
  slippageAmount: number
  reason: string | null
}

export type BacktestEquityResponse = {
  valuationAt: string
  totalAssetValue: number
  cashBalance: number
  returnRate: number
  drawdownRate: number
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type CreateBacktestRunRequest = {
  strategyId: number
  periodStart: string
  periodEnd: string
  quoteCurrency: string
  initialCapital: number
  feeRate: number
  slippageBps: number
  executionPriceRule: ExecutionPriceRule
  engineVersion: string
  marketDataVersion: string
}

/* 화면이 쓰는 모델. 식별자는 라우트 파라미터와 맞추려고 문자열로 두고 시각은 Date 로 바꾼다. */
export type BacktestRun = {
  id: string
  strategyId: string
  status: BacktestRunStatus
  periodStart: Date
  periodEnd: Date
  timeframe: string
  initialCapital: number
  feeRate: number
  slippageBps: number
  queuedAt: Date
  startedAt: Date | null
  completedAt: Date | null
}

export type BacktestResult = {
  finalAssetValue: number
  cumulativeReturn: number
  cagr: number | null
  mdd: number
  sharpeRatio: number | null
  volatility: number | null
  winRate: number | null
  tradeCount: number
  totalFee: number
  benchmarkReturn: number | null
}

export type BacktestTrade = {
  id: string
  marketCode: string
  side: TradeSide
  filledAt: Date
  executedPrice: number
  quantity: number
  notional: number
  feeAmount: number
  reason: string | null
}

export type EquityPoint = {
  valuationAt: Date
  totalAssetValue: number
  returnRate: number
  drawdownRate: number
}

export function toBacktestRun(response: BacktestRunResponse): BacktestRun {
  return {
    id: response.backtestId,
    strategyId: String(response.strategyId),
    status: response.status,
    periodStart: new Date(response.periodStart),
    periodEnd: new Date(response.periodEnd),
    timeframe: response.timeframe,
    initialCapital: response.initialCapital,
    feeRate: response.feeRate,
    slippageBps: response.slippageBps,
    queuedAt: new Date(response.queuedAt),
    startedAt: response.startedAt === null ? null : new Date(response.startedAt),
    completedAt: response.completedAt === null ? null : new Date(response.completedAt),
  }
}

export function toBacktestResult(response: BacktestResultResponse): BacktestResult {
  const { backtestId: _ignored, ...metrics } = response
  return metrics
}

export function toBacktestTrade(response: BacktestTradeResponse): BacktestTrade {
  return {
    id: response.tradeId,
    marketCode: response.assetMarketCode,
    side: response.side,
    filledAt: new Date(response.filledAt),
    executedPrice: response.executedPrice,
    quantity: response.quantity,
    notional: response.notional,
    feeAmount: response.feeAmount,
    reason: response.reason,
  }
}

export function toEquityPoint(response: BacktestEquityResponse): EquityPoint {
  return {
    valuationAt: new Date(response.valuationAt),
    totalAssetValue: response.totalAssetValue,
    returnRate: response.returnRate,
    drawdownRate: response.drawdownRate,
  }
}
