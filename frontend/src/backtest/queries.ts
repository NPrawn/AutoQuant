import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createBacktestRun,
  fetchBacktestEquities,
  fetchBacktestResult,
  fetchBacktestRun,
  fetchBacktestRuns,
  fetchBacktestTrades,
  fetchCandleCoverage,
  maxPageSize,
} from './api'
import { fetchCandles, fetchChartAssets } from '../market/api'
import { CANDLE_LIMIT } from '../market/model'
import { hasUnsettledRun } from './history'
import { isRunInProgress } from './labels'
import { benchmarkMarketCode, type BenchmarkCandle } from './performance'
import { coverageFromInstants, type CandleCoverage } from './periodBounds'
import { toBacktestResult, toBacktestRun, toBacktestTrade, toEquityPoint } from './types'
import type { BacktestRunResponse, BacktestRunStatus, BacktestTrade, EquityPoint } from './types'

export const backtestsQueryKey = ['backtests'] as const
export const backtestRunQueryKey = (backtestId: string) => [...backtestsQueryKey, backtestId] as const
export const backtestResultQueryKey = (backtestId: string) => [...backtestRunQueryKey(backtestId), 'result'] as const
export const backtestEquityQueryKey = (backtestId: string) => [...backtestRunQueryKey(backtestId), 'equity'] as const
export const backtestTradesQueryKey = (backtestId: string, page: number) =>
  [...backtestRunQueryKey(backtestId), 'trades', page] as const
/* 실행 목록. 식별자는 UUID 라 'list' 와 겹치지 않는다. 새 실행을 접수하면 이 키 아래를 무효화한다. */
export const backtestListQueryKey = [...backtestsQueryKey, 'list'] as const
export const backtestPageQueryKey = (page: number) => [...backtestListQueryKey, page] as const
export const backtestHistoryQueryKey = [...backtestListQueryKey, 'all'] as const

/* 접수된 실행은 워커가 처리한다. 끝나기 전까지 상태를 다시 물어야 하므로 폴링 간격을 둔다. */
const statusPollIntervalMs = 2000
/* 목록은 결과 화면만큼 급하지 않아 느슨하게 갱신한다. 진행 중인 실행이 없으면 멈춘다. */
const listPollIntervalMs = 5000

/* 모르는 상태는 끝난 것으로 본다. 진행 중으로 보면 서버가 상태를 하나 늘렸을 때 폴링과 폼 잠금이 영원히 풀리지 않는다. */
const isSettled = (status: BacktestRunStatus): boolean => !isRunInProgress(status)

export function useCreateBacktestRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: createBacktestRun,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: backtestListQueryKey }),
  })
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
    /* 조회가 실패해도 인터벌은 돌아간다. 없는 실행 id 로 2초마다 404 를 보내지 않게 오류에서도 멈춘다. */
    refetchInterval: (query) =>
      query.state.status === 'error' || (query.state.data && isSettled(query.state.data.status)) ? false : statusPollIntervalMs,
    refetchIntervalInBackground: true,
  })
}

/* 실행 이력 한 페이지. 페이지를 넘길 때 이전 표를 유지해 깜빡이지 않는다. */
export const runPageSize = 20

export function useBacktestRuns(page: number) {
  return useQuery({
    queryKey: backtestPageQueryKey(page),
    queryFn: () => fetchBacktestRuns(page, runPageSize),
    select: (response) => ({ ...response, content: response.content.map(toBacktestRun) }),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => (query.state.data && hasUnsettledRun(query.state.data.content) ? listPollIntervalMs : false),
  })
}

/*
 * 전략별 이력은 서버 필터가 없어 전체를 받아 화면이 거른다. 페이지가 많으면 앞에서부터
 * 정해진 수까지만 이어 받는다. 회원 한 명의 실행 수로는 충분하고 넘치는 옛 실행은 잘린다.
 */
const maxHistoryPages = 5

async function fetchAllBacktestRuns(): Promise<BacktestRunResponse[]> {
  const runs: BacktestRunResponse[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages && page < maxHistoryPages) {
    const response = await fetchBacktestRuns(page, maxPageSize)
    runs.push(...response.content)
    totalPages = response.totalPages
    page += 1
  }
  return runs
}

export function useBacktestHistory() {
  return useQuery({
    queryKey: backtestHistoryQueryKey,
    queryFn: fetchAllBacktestRuns,
    select: (responses) => responses.map(toBacktestRun),
    refetchInterval: (query) => (query.state.data && hasUnsettledRun(query.state.data) ? listPollIntervalMs : false),
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

/*
 * 캔들 보유 구간. 서버가 수집 종목 전체를 합쳐 재 준다. 대상이 백테스트 후보군과 같아
 * 화면이 고른 기간을 엔진이 그대로 평가한다. 하루에 한 번꼴로 바뀌는 값이라 오래 들고 있는다.
 */
export const candleCoverageQueryKey = ['candle-coverage'] as const

/* 기간 경계는 일 단위로 고르므로 일봉 구간을 본다. */
const coverageIntervalMinutes = 1440

export function useCandleCoverage() {
  return useQuery({
    queryKey: candleCoverageQueryKey,
    queryFn: async (): Promise<CandleCoverage | null> => {
      const response = await fetchCandleCoverage(coverageIntervalMinutes)
      return coverageFromInstants(response.start, response.end)
    },
    staleTime: 60 * 60 * 1000,
  })
}

/*
 * 단순보유 비교선. 종목 목록에서 벤치마크 종목을 찾아 일봉을 받는다. 없으면 빈 목록이라 비교선만 빠진다.
 * 일봉 조회 상한이 1,000개라 그보다 긴 백테스트는 앞부분 비교가 비지만 전략 곡선은 그대로 그린다.
 */
export const backtestBenchmarkQueryKey = [...backtestsQueryKey, 'benchmark', benchmarkMarketCode] as const

export function useBacktestBenchmark(enabled: boolean) {
  return useQuery({
    queryKey: backtestBenchmarkQueryKey,
    queryFn: async ({ signal }): Promise<BenchmarkCandle[]> => {
      const assets = await fetchChartAssets(signal)
      const benchmark = assets.find((asset) => asset.marketCode === benchmarkMarketCode)
      if (benchmark === undefined) {
        return []
      }
      const response = await fetchCandles(benchmark.assetId, 1440, CANDLE_LIMIT, signal)
      return response.candles
    },
    enabled,
    staleTime: 60 * 60 * 1000,
  })
}

/* 거래 내역은 사용자가 넘겨 보므로 페이지를 그대로 노출한다. */
export const tradePageSize = 20

/*
 * 체결 차트가 쓰는 전량 조회. 마커를 한두 페이지만 찍으면 실제보다 드물게 거래한 것처럼 보여
 * 앞에서부터 정해진 페이지 수까지 이어 받는다. 표의 페이지 조회와 캐시를 나눠 쓴다.
 */
const maxTradePages = 10

async function fetchAllTrades(backtestId: string): Promise<BacktestTrade[]> {
  const trades: BacktestTrade[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages && page < maxTradePages) {
    const response = await fetchBacktestTrades(backtestId, page, maxPageSize)
    trades.push(...response.content.map(toBacktestTrade))
    totalPages = response.totalPages
    page += 1
  }
  return trades
}

export function useBacktestTradeMarkers(backtestId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: [...backtestTradesQueryKey(backtestId ?? '', 0), 'all'],
    queryFn: () => fetchAllTrades(backtestId ?? ''),
    enabled: backtestId !== null && enabled,
    staleTime: Infinity,
  })
}

export function useBacktestTrades(backtestId: string | null, page: number, enabled: boolean) {
  return useQuery({
    queryKey: backtestTradesQueryKey(backtestId ?? '', page),
    queryFn: () => fetchBacktestTrades(backtestId ?? '', page, tradePageSize),
    select: (response) => ({ ...response, content: response.content.map(toBacktestTrade) }),
    enabled: backtestId !== null && enabled,
    /* 페이지를 넘길 때 표가 로딩 블록으로 바뀌면 스크롤이 튄다. 이전 페이지를 보이며 바꾼다. */
    placeholderData: keepPreviousData,
  })
}
