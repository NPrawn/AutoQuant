import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { maxPageSize } from '../backtest/api'
import { toEquityPoint } from '../backtest/types'
import type { EquityPoint } from '../backtest/types'
import {
  createSimulationRun,
  fetchSimulationEquities,
  fetchSimulationPositions,
  fetchSimulationResult,
  fetchSimulationRun,
  fetchSimulationRuns,
  fetchSimulationTrades,
  startSimulationRun,
  stopSimulationRun,
} from './api'
import { toSimulationPosition, toSimulationResult, toSimulationRun, toSimulationTrade } from './types'
import type { CreateSimulationRunRequest, SimulationRunResponse } from './types'

export const simulationsQueryKey = ['simulations'] as const
export const simulationListQueryKey = [...simulationsQueryKey, 'list'] as const
export const simulationPageQueryKey = (page: number) => [...simulationListQueryKey, page] as const
export const simulationRunQueryKey = (simulationId: string) => [...simulationsQueryKey, simulationId] as const
export const simulationPositionsQueryKey = (simulationId: string) => [...simulationRunQueryKey(simulationId), 'positions'] as const
export const simulationResultQueryKey = (simulationId: string) => [...simulationRunQueryKey(simulationId), 'result'] as const
export const simulationEquityQueryKey = (simulationId: string) => [...simulationRunQueryKey(simulationId), 'equity'] as const
export const simulationTradesQueryKey = (simulationId: string, page: number) =>
  [...simulationRunQueryKey(simulationId), 'trades', page] as const

/*
 * 운용 중인 실행은 봉이 마감될 때만 바뀐다. 가장 짧은 봉이 1시간이라 자주 물을 이유가 없지만,
 * 정지·실패 전이는 바로 보여야 해서 15초로 둔다. 운용 중이 아니면 묻지 않는다.
 */
const livePollIntervalMs = 15_000

const liveInterval = (status: SimulationRunResponse['status'] | undefined) => (status === 'RUNNING' ? livePollIntervalMs : false)

export const runPageSize = 12

export function useSimulationRuns(page: number) {
  return useQuery({
    queryKey: simulationPageQueryKey(page),
    queryFn: () => fetchSimulationRuns(page, runPageSize),
    select: (response) => ({ ...response, content: response.content.map(toSimulationRun) }),
    placeholderData: keepPreviousData,
    refetchInterval: (query) =>
      query.state.data?.content.some((run) => run.status === 'RUNNING') ? livePollIntervalMs : false,
  })
}

/*
 * 운용 중인 실행 수. 로그아웃 확인 모달이 "계속 실행된다"고 알릴 때 쓴다. 상태 필터 API 가 없어
 * 첫 페이지 최대치를 받아 세므로 100건을 넘는 회원은 그 이상은 세지 않는다.
 */
export const runningSimulationCountQueryKey = [...simulationListQueryKey, 'running-count'] as const

/*
 * 전략 상세가 전략별로 거르는 전체 이력. 서버에 전략 필터가 없어 앞에서부터 정해진 페이지까지 이어 받는다.
 * 백테스트 이력과 같은 방식이다. 운용 중이 하나라도 있으면 상태가 바뀔 수 있어 15초로 다시 묻는다.
 */
export const simulationHistoryQueryKey = [...simulationListQueryKey, 'all'] as const
const maxHistoryPages = 5

async function fetchAllSimulationRuns(): Promise<SimulationRunResponse[]> {
  const runs: SimulationRunResponse[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages && page < maxHistoryPages) {
    const response = await fetchSimulationRuns(page, maxPageSize)
    runs.push(...response.content)
    totalPages = response.totalPages
    page += 1
  }
  return runs
}

export function useSimulationHistory() {
  return useQuery({
    queryKey: simulationHistoryQueryKey,
    queryFn: fetchAllSimulationRuns,
    select: (responses) => responses.map(toSimulationRun),
    refetchInterval: (query) => (query.state.data?.some((run) => run.status === 'RUNNING') ? livePollIntervalMs : false),
  })
}

export function useRunningSimulationCount() {
  return useQuery({
    queryKey: runningSimulationCountQueryKey,
    queryFn: () => fetchSimulationRuns(0, maxPageSize),
    select: (response) => response.content.filter((run) => run.status === 'RUNNING').length,
    staleTime: 60_000,
  })
}

export function useSimulationRun(simulationId: string | undefined) {
  return useQuery({
    queryKey: simulationRunQueryKey(simulationId ?? ''),
    queryFn: () => fetchSimulationRun(simulationId ?? ''),
    select: toSimulationRun,
    enabled: simulationId !== undefined,
    refetchInterval: (query) => liveInterval(query.state.data?.status),
  })
}

/* live 가 참이면 실행 상태와 같은 주기로 다시 묻는다. 정지된 실행은 한 번만 읽는다. */
export function useSimulationPositions(simulationId: string, live: boolean) {
  return useQuery({
    queryKey: simulationPositionsQueryKey(simulationId),
    queryFn: () => fetchSimulationPositions(simulationId),
    select: (responses) => responses.map(toSimulationPosition),
    refetchInterval: live ? livePollIntervalMs : false,
  })
}

/* 첫 봉이 마감되기 전에는 결과 행이 없어 404 가 온다. 오류가 아니라 아직 없는 것이다. */
export function useSimulationResult(simulationId: string, live: boolean) {
  return useQuery({
    queryKey: simulationResultQueryKey(simulationId),
    queryFn: () => fetchSimulationResult(simulationId),
    select: toSimulationResult,
    refetchInterval: live ? livePollIntervalMs : false,
  })
}

const maxEquityPages = 20

async function fetchEquityCurve(simulationId: string): Promise<EquityPoint[]> {
  const points: EquityPoint[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages && page < maxEquityPages) {
    const response = await fetchSimulationEquities(simulationId, page, maxPageSize)
    points.push(...response.content.map(toEquityPoint))
    totalPages = response.totalPages
    page += 1
  }
  return points
}

export function useSimulationEquityCurve(simulationId: string, live: boolean) {
  return useQuery({
    queryKey: simulationEquityQueryKey(simulationId),
    queryFn: () => fetchEquityCurve(simulationId),
    refetchInterval: live ? livePollIntervalMs : false,
  })
}

export const tradePageSize = 20

export function useSimulationTrades(simulationId: string, page: number, live: boolean) {
  return useQuery({
    queryKey: simulationTradesQueryKey(simulationId, page),
    queryFn: () => fetchSimulationTrades(simulationId, page, tradePageSize),
    select: (response) => ({ ...response, content: response.content.map(toSimulationTrade) }),
    placeholderData: keepPreviousData,
    refetchInterval: live ? livePollIntervalMs : false,
  })
}

/*
 * 생성과 시작을 한 번에 한다. 시작이 실패하면 CREATED 실행이 남는데, 목록에서 시작 전으로
 * 보이고 거기서 다시 시작할 수 있으므로 여기서 지우지 않는다.
 */
export function useLaunchSimulation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (body: CreateSimulationRunRequest) => {
      const created = await createSimulationRun(body)
      return startSimulationRun(created.simulationId)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: simulationListQueryKey }),
  })
}

function useTransition(mutationFn: (simulationId: string) => Promise<SimulationRunResponse>) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn,
    onSuccess: (response) => {
      queryClient.setQueryData(simulationRunQueryKey(response.simulationId), response)
      return queryClient.invalidateQueries({ queryKey: simulationListQueryKey })
    },
  })
}

export function useStartSimulation() {
  return useTransition(startSimulationRun)
}

export function useStopSimulation() {
  return useTransition(stopSimulationRun)
}

/* 보유 종목 현재가는 차트와 같은 구독을 쓴다. 모의투자 화면이 쓰던 이름 그대로 다시 내보낸다. */
export { useLivePrices, type LivePrice } from '../market/queries'
