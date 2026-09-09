import { useMutation, useQuery } from '@tanstack/react-query'

import {
  createBacktestRun,
  fetchBacktestEquities,
  fetchBacktestResult,
  fetchBacktestRun,
  fetchBacktestTrades,
  maxPageSize,
} from './api'
import { toBacktestResult, toBacktestRun, toBacktestTrade, toEquityPoint } from './types'
import type { BacktestRunStatus, EquityPoint } from './types'

export const backtestsQueryKey = ['backtests'] as const
export const backtestRunQueryKey = (backtestId: string) => [...backtestsQueryKey, backtestId] as const
export const backtestResultQueryKey = (backtestId: string) => [...backtestRunQueryKey(backtestId), 'result'] as const
export const backtestEquityQueryKey = (backtestId: string) => [...backtestRunQueryKey(backtestId), 'equity'] as const
export const backtestTradesQueryKey = (backtestId: string, page: number) =>
  [...backtestRunQueryKey(backtestId), 'trades', page] as const

/* 접수된 실행은 워커가 처리한다. 끝나기 전까지 상태를 다시 물어야 하므로 폴링 간격을 둔다. */
const statusPollIntervalMs = 2000

const isSettled = (status: BacktestRunStatus): boolean => status === 'COMPLETED' || status === 'FAILED'

export function useCreateBacktestRun() {
  return useMutation({ mutationFn: createBacktestRun })
}

/*
 * 대기와 계산 중에는 다시 조회하고 완료나 실패로 바뀌면 멈춘다.
 * 다른 탭을 보는 동안에도 계속 물어야 돌아왔을 때 이미 끝난 상태를 본다.
 */
export function useBacktestRun(backtestId: string | null) {
  return useQuery({
    queryKey: backtestRunQueryKey(backtestId ?? ''),
    queryFn: () => fetchBacktestRun(backtestId ?? ''),
    select: toBacktestRun,
    enabled: backtestId !== null,
    refetchInterval: (query) => (query.state.data && isSettled(query.state.data.status) ? false : statusPollIntervalMs),
    refetchIntervalInBackground: true,
  })
}

export function useBacktestResult(backtestId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: backtestResultQueryKey(backtestId ?? ''),
    queryFn: () => fetchBacktestResult(backtestId ?? ''),
    select: toBacktestResult,
    enabled: backtestId !== null && enabled,
  })
}

/*
 * 자산 곡선은 페이지로 나뉘어 온다. 한 번에 다 받으면 요청이 너무 많아지므로 앞에서부터
 * 정해진 페이지 수까지만 이어 받는다. 곡선을 그리기에는 충분하고 남은 구간은 잘린다.
 */
const maxEquityPages = 20

async function fetchEquityCurve(backtestId: string): Promise<EquityPoint[]> {
  const points: EquityPoint[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages && page < maxEquityPages) {
    const response = await fetchBacktestEquities(backtestId, page, maxPageSize)
    points.push(...response.content.map(toEquityPoint))
    totalPages = response.totalPages
    page += 1
  }
  return points
}

export function useBacktestEquityCurve(backtestId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: backtestEquityQueryKey(backtestId ?? ''),
    queryFn: () => fetchEquityCurve(backtestId ?? ''),
    enabled: backtestId !== null && enabled,
  })
}

/* 거래 내역은 사용자가 넘겨 보므로 페이지를 그대로 노출한다. */
export const tradePageSize = 20

export function useBacktestTrades(backtestId: string | null, page: number, enabled: boolean) {
  return useQuery({
    queryKey: backtestTradesQueryKey(backtestId ?? '', page),
    queryFn: () => fetchBacktestTrades(backtestId ?? '', page, tradePageSize),
    select: (response) => ({ ...response, content: response.content.map(toBacktestTrade) }),
    enabled: backtestId !== null && enabled,
  })
}
