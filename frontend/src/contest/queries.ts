import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'

import {
  fetchContest,
  fetchContestDatasets,
  fetchContestProgress,
  fetchContestRankings,
  fetchContestResults,
  fetchContests,
  fetchMyContestResult,
  submitContestEntry,
} from './api'
import { awaitingScore } from './types'
import type { SubmitContestEntryRequest } from './types'

/* 대회 쿼리 키의 뿌리. 제출이 성공하면 이 키 아래를 무효화해 남은 횟수를 다시 읽는다. */
export const contestsQueryKey = ['contests'] as const
export const contestQueryKey = (roundId: string) => [...contestsQueryKey, roundId] as const
export const contestProgressQueryKey = (roundId: string) => [...contestQueryKey(roundId), 'progress'] as const
export const contestResultsQueryKey = (roundId: string) => [...contestQueryKey(roundId), 'results'] as const
export const contestMyResultQueryKey = (roundId: string) => [...contestResultsQueryKey(roundId), 'me'] as const
export const contestDatasetsQueryKey = (roundId: string) => [...contestQueryKey(roundId), 'datasets'] as const

/* 채점은 참가 수 × 4벌이라 몇 분이 걸린다. 끝나면 멈춘다. */
const progressPollIntervalMs = 5000

export function useContests() {
  return useQuery({ queryKey: contestsQueryKey, queryFn: fetchContests })
}

export function useContest(roundId: string | undefined) {
  return useQuery({
    queryKey: contestQueryKey(roundId ?? ''),
    queryFn: () => fetchContest(roundId ?? ''),
    enabled: roundId !== undefined,
  })
}

export function useSubmitContestEntry(roundId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: SubmitContestEntryRequest) => submitContestEntry(roundId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: contestsQueryKey }),
  })
}

export function useContestProgress(roundId: string | undefined) {
  return useQuery({
    queryKey: contestProgressQueryKey(roundId ?? ''),
    queryFn: () => fetchContestProgress(roundId ?? ''),
    enabled: roundId !== undefined,
    refetchInterval: (query) => (query.state.data?.status === 'SCORING' ? progressPollIntervalMs : false),
    refetchIntervalInBackground: true,
  })
}

/* 결과는 채점 중에도 200 이다. 종료 전 409 는 데이터셋 조회만이고, 화면은 진행 상황으로 켜고 끈다. */
export function useContestResults(roundId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: contestResultsQueryKey(roundId ?? ''),
    queryFn: () => fetchContestResults(roundId ?? ''),
    enabled: roundId !== undefined && enabled,
  })
}

export function useMyContestResult(roundId: string | undefined, enabled: boolean) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: contestMyResultQueryKey(roundId ?? ''),
    queryFn: () => fetchMyContestResult(roundId ?? ''),
    enabled: roundId !== undefined && enabled,
    /* 제출하지 않았으면 404 다. 없는 것이 답이라 다시 묻지 않는다. */
    retry: false,
    /* 제출 직후에는 백테스트 4벌이 돌고, 끝난 뒤에도 스케줄러가 점수를 쓸 때까지 다시 묻는다. */
    refetchInterval: (current) => {
      const data = current.state.data
      return data !== undefined && awaitingScore(data) ? progressPollIntervalMs : false
    },
    refetchIntervalInBackground: true,
  })

  /* 채점되는 순간 순위표와 목록의 내 순위도 바뀐다. 기다리던 결과가 채점됐을 때 한 번만 대회 쿼리를 다시 읽는다. */
  const waiting = useRef(false)
  useEffect(() => {
    if (query.data === undefined) {
      return
    }
    if (awaitingScore(query.data)) {
      waiting.current = true
      return
    }
    if (waiting.current) {
      waiting.current = false
      void queryClient.invalidateQueries({ queryKey: contestsQueryKey })
    }
  }, [query.data, queryClient])

  return query
}

export function useContestDatasets(roundId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: contestDatasetsQueryKey(roundId ?? ''),
    queryFn: () => fetchContestDatasets(roundId ?? ''),
    enabled: roundId !== undefined && enabled,
  })
}

/*
 * 회차 랭킹 한 페이지. 조건을 바꾸면 키가 달라지고, 이전 표를 유지해 표가 깜빡이지 않는다.
 * 종료된 회차만 열리므로 회차를 고르기 전에는 요청하지 않는다.
 */
export const contestRankingsQueryKey = (roundId: string, params: Record<string, string>) =>
  [...contestQueryKey(roundId), 'rankings', params] as const

export function useContestRankings(roundId: string | null, params: Record<string, string>) {
  return useQuery({
    queryKey: contestRankingsQueryKey(roundId ?? '', params),
    queryFn: () => fetchContestRankings(roundId ?? '', params),
    enabled: roundId !== null,
    placeholderData: keepPreviousData,
  })
}
