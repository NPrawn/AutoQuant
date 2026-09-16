import { request } from '../api/client'
import type {
  BacktestEquityResponse,
  BacktestResultResponse,
  BacktestRunCreatedResponse,
  BacktestRunResponse,
  BacktestTradeResponse,
  CandleCoverageResponse,
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

/* 내 실행 목록. 서버가 접수 시각 내림차순으로 준다. */
export function fetchBacktestRuns(page: number, size: number): Promise<PageResponse<BacktestRunResponse>> {
  return request<PageResponse<BacktestRunResponse>>(`/backtests?page=${page}&size=${size}`)
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

/*
 * 수집 종목이 보유한 봉 구간. 백테스트가 고를 수 있는 기간의 하한을 여기서 받는다.
 * 종목별 최근 봉으로 추정하던 때는 조회 상한 1,000개에 막혀 과거가 쌓일수록 시작일이 잘렸다.
 */
export function fetchCandleCoverage(intervalMinutes: number): Promise<CandleCoverageResponse> {
  return request<CandleCoverageResponse>(`/candles/coverage?intervalMinutes=${intervalMinutes}`)
}
