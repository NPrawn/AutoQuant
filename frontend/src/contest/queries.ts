import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  fetchContest,
  fetchContestDatasets,
  fetchContestProgress,
  fetchContestResults,
  fetchContests,
  fetchMyContestResult,
  submitContestEntry,
} from './api'
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

/* 결과 세 가지는 종료 전에 부르면 CONTEST-409-003 이라 진행 상황이 종료를 알린 뒤에만 켠다. */
export function useContestResults(roundId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: contestResultsQueryKey(roundId ?? ''),
    queryFn: () => fetchContestResults(roundId ?? ''),
    enabled: roundId !== undefined && enabled,
  })
}

export function useMyContestResult(roundId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: contestMyResultQueryKey(roundId ?? ''),
    queryFn: () => fetchMyContestResult(roundId ?? ''),
    enabled: roundId !== undefined && enabled,
    /* 제출하지 않았거나 결과가 없으면 404 다. 없는 것이 답이라 다시 묻지 않는다. */
    retry: false,
  })
}

export function useContestDatasets(roundId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: contestDatasetsQueryKey(roundId ?? ''),
    queryFn: () => fetchContestDatasets(roundId ?? ''),
    enabled: roundId !== undefined && enabled,
  })
}
