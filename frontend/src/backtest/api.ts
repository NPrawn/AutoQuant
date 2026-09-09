import { request } from '../api/client'
import type {
  BacktestEquityResponse,
  BacktestResultResponse,
  BacktestRunCreatedResponse,
  BacktestRunResponse,
  BacktestTradeResponse,
  CreateBacktestRunRequest,
  PageResponse,
} from './types'

/* 실행 요청은 202 로 접수만 된다. 계산은 워커가 하고 화면은 상태를 다시 조회한다. */
export function createBacktestRun(body: CreateBacktestRunRequest): Promise<BacktestRunCreatedResponse> {
  return request<BacktestRunCreatedResponse>('/backtests', { method: 'POST', body })
}

export function fetchBacktestRun(backtestId: string): Promise<BacktestRunResponse> {
  return request<BacktestRunResponse>(`/backtests/${encodeURIComponent(backtestId)}`)
}

export function fetchBacktestResult(backtestId: string): Promise<BacktestResultResponse> {
  return request<BacktestResultResponse>(`/backtests/${encodeURIComponent(backtestId)}/result`)
}

/* 백엔드 상한이 100 이라 한 번에 그 이상 요청하지 않는다. */
export const maxPageSize = 100

export function fetchBacktestTrades(
  backtestId: string,
  page: number,
  size: number,
): Promise<PageResponse<BacktestTradeResponse>> {
  return request<PageResponse<BacktestTradeResponse>>(
    `/backtests/${encodeURIComponent(backtestId)}/trades?page=${page}&size=${size}`,
  )
}

export function fetchBacktestEquities(
  backtestId: string,
  page: number,
  size: number,
): Promise<PageResponse<BacktestEquityResponse>> {
  return request<PageResponse<BacktestEquityResponse>>(
    `/backtests/${encodeURIComponent(backtestId)}/equity?page=${page}&size=${size}`,
  )
}
