import type { ExecutionPriceRule, PositionSide, TradeSide } from '../backtest/types'
import type { RebalancePeriod, Timeframe } from '../strategy/types'

/*
 * backend/api 의 trading.simulation 응답과 같은 모양. 백테스트와 같은 원장 구조라 체결·자산 곡선은
 * 필드가 같고, 실행은 기간 대신 시작·정지 시각과 마지막 봉 시각을 가진다.
 */
export type SimulationRunStatus = 'CREATED' | 'RUNNING' | 'STOPPED' | 'FAILED'

export type CreateSimulationRunRequest = {
  strategyId: number
  initialCapital: number
  feeRate: number
  slippageBps: number
  executionPriceRule: ExecutionPriceRule
  engineVersion: string
}

export type SimulationRunCreatedResponse = {
  simulationId: string
  status: SimulationRunStatus
}

export type SimulationRunResponse = {
  simulationId: string
  strategyId: number
  status: SimulationRunStatus
  timeframe: Timeframe
  quoteCurrency: string
  initialCapital: number
  cashBalance: number
  feeRate: number
  slippageBps: number
  rebalanceInterval: RebalancePeriod
  executionPriceRule: ExecutionPriceRule
  engineVersion: string
  startedAt: string | null
  stoppedAt: string | null
  lastCandleAt: string | null
  lastRebalanceAt: string | null
  failureReason: string | null
}

/* 종목은 식별자만 온다. 마켓 코드와 이름은 /assets 목록으로 맞춘다. */
export type SimulationPositionResponse = {
  assetId: string
  positionSide: PositionSide
  quantity: number
  averageEntryPrice: number
  updatedAt: string
}

export type SimulationTradeResponse = {
  tradeId: string
  assetId: string
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

/* 운용 중에는 마지막 봉 기준으로 같은 행이 갱신된다. 비율 의미는 백테스트 결과와 같다. */
export type SimulationResultResponse = {
  asOf: string
  assetValue: number
  cumulativeReturn: number
  mdd: number
  sharpeRatio: number | null
  volatility: number | null
  winRate: number | null
  tradeCount: number
  totalFee: number
}

/* 화면 모델. 식별자는 라우트 파라미터와 맞추려고 문자열로 두고 시각은 Date 로 바꾼다. */
export type SimulationRun = {
  id: string
  strategyId: string
  status: SimulationRunStatus
  timeframe: Timeframe
  initialCapital: number
  cashBalance: number
  feeRate: number
  slippageBps: number
  rebalanceInterval: RebalancePeriod
  startedAt: Date | null
  stoppedAt: Date | null
  lastCandleAt: Date | null
  lastRebalanceAt: Date | null
  failureReason: string | null
}

export type SimulationPosition = {
  assetId: string
  quantity: number
  averageEntryPrice: number
  updatedAt: Date
}

export type SimulationTrade = {
  id: string
  assetId: string
  side: TradeSide
  filledAt: Date
  executedPrice: number
  quantity: number
  notional: number
  feeAmount: number
  reason: string | null
}

export type SimulationResult = {
  asOf: Date
  assetValue: number
  cumulativeReturn: number
  mdd: number
  sharpeRatio: number | null
  volatility: number | null
  winRate: number | null
  tradeCount: number
  totalFee: number
}

const toDate = (value: string | null): Date | null => (value === null ? null : new Date(value))

export function toSimulationRun(response: SimulationRunResponse): SimulationRun {
  return {
    id: response.simulationId,
    strategyId: String(response.strategyId),
    status: response.status,
    timeframe: response.timeframe,
    initialCapital: response.initialCapital,
    cashBalance: response.cashBalance,
    feeRate: response.feeRate,
    slippageBps: response.slippageBps,
    rebalanceInterval: response.rebalanceInterval,
    startedAt: toDate(response.startedAt),
    stoppedAt: toDate(response.stoppedAt),
    lastCandleAt: toDate(response.lastCandleAt),
    lastRebalanceAt: toDate(response.lastRebalanceAt),
    failureReason: response.failureReason,
  }
}

export function toSimulationPosition(response: SimulationPositionResponse): SimulationPosition {
  return {
    assetId: response.assetId,
    quantity: response.quantity,
    averageEntryPrice: response.averageEntryPrice,
    updatedAt: new Date(response.updatedAt),
  }
}

export function toSimulationTrade(response: SimulationTradeResponse): SimulationTrade {
  return {
    id: response.tradeId,
    assetId: response.assetId,
    side: response.side,
    filledAt: new Date(response.filledAt),
    executedPrice: response.executedPrice,
    quantity: response.quantity,
    notional: response.notional,
    feeAmount: response.feeAmount,
    reason: response.reason,
  }
}

export function toSimulationResult(response: SimulationResultResponse): SimulationResult {
  return { ...response, asOf: new Date(response.asOf) }
}
