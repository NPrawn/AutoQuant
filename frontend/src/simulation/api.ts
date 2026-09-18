import { request } from '../api/client'
import type { BacktestEquityResponse, PageResponse } from '../backtest/types'
import type {
  CreateSimulationRunRequest,
  SimulationPositionResponse,
  SimulationResultResponse,
  SimulationRunCreatedResponse,
  SimulationRunResponse,
  SimulationTradeResponse,
} from './types'

const base = '/simulations'
const runPath = (simulationId: string) => `${base}/${encodeURIComponent(simulationId)}`

/* 생성은 CREATED 로 접수만 된다. 실시간 봉을 받기 시작하는 것은 start 다. */
export function createSimulationRun(body: CreateSimulationRunRequest): Promise<SimulationRunCreatedResponse> {
  return request<SimulationRunCreatedResponse>(base, { method: 'POST', body })
}

/* CREATED 와 STOPPED 에서만 시작할 수 있고, RUNNING 에서만 정지할 수 있다. 어긋나면 409 다. */
export function startSimulationRun(simulationId: string): Promise<SimulationRunResponse> {
  return request<SimulationRunResponse>(`${runPath(simulationId)}/start`, { method: 'POST' })
}

export function stopSimulationRun(simulationId: string): Promise<SimulationRunResponse> {
  return request<SimulationRunResponse>(`${runPath(simulationId)}/stop`, { method: 'POST' })
}

export function fetchSimulationRuns(page: number, size: number): Promise<PageResponse<SimulationRunResponse>> {
  return request<PageResponse<SimulationRunResponse>>(`${base}?page=${page}&size=${size}`)
}

export function fetchSimulationRun(simulationId: string): Promise<SimulationRunResponse> {
  return request<SimulationRunResponse>(runPath(simulationId))
}

export function fetchSimulationPositions(simulationId: string): Promise<SimulationPositionResponse[]> {
  return request<SimulationPositionResponse[]>(`${runPath(simulationId)}/positions`)
}

export function fetchSimulationTrades(
  simulationId: string,
  page: number,
  size: number,
): Promise<PageResponse<SimulationTradeResponse>> {
  return request<PageResponse<SimulationTradeResponse>>(`${runPath(simulationId)}/trades?page=${page}&size=${size}`)
}

/* 자산 곡선 행은 백테스트와 같은 모양이라 그 응답 타입을 그대로 쓴다. */
export function fetchSimulationEquities(
  simulationId: string,
  page: number,
  size: number,
): Promise<PageResponse<BacktestEquityResponse>> {
  return request<PageResponse<BacktestEquityResponse>>(`${runPath(simulationId)}/equity?page=${page}&size=${size}`)
}

export function fetchSimulationResult(simulationId: string): Promise<SimulationResultResponse> {
  return request<SimulationResultResponse>(`${runPath(simulationId)}/result`)
}
