import { request } from '../api/client'
import type {
  ContestDatasetsResponse,
  ContestEntryResponse,
  ContestMyResultResponse,
  ContestProgressResponse,
  ContestResultsResponse,
  ContestRoundDetailResponse,
  ContestRoundListResponse,
  SubmitContestEntryRequest,
} from './types'

export function fetchContests(): Promise<ContestRoundListResponse> {
  return request<ContestRoundListResponse>('/contests')
}

export function fetchContest(roundId: string): Promise<ContestRoundDetailResponse> {
  return request<ContestRoundDetailResponse>(`/contests/${encodeURIComponent(roundId)}`)
}

/* 제출과 교체가 같은 요청이다. 같은 전략을 다시 내도 횟수에 들어간다. */
export function submitContestEntry(
  roundId: string,
  body: SubmitContestEntryRequest,
): Promise<ContestEntryResponse> {
  return request<ContestEntryResponse>(`/contests/${encodeURIComponent(roundId)}/entry`, {
    method: 'PUT',
    body,
  })
}

export function fetchContestProgress(roundId: string): Promise<ContestProgressResponse> {
  return request<ContestProgressResponse>(`/contests/${encodeURIComponent(roundId)}/progress`)
}

export function fetchContestResults(roundId: string): Promise<ContestResultsResponse> {
  return request<ContestResultsResponse>(`/contests/${encodeURIComponent(roundId)}/results`)
}

export function fetchMyContestResult(roundId: string): Promise<ContestMyResultResponse> {
  return request<ContestMyResultResponse>(`/contests/${encodeURIComponent(roundId)}/results/me`)
}

/* 채점용 데이터는 마감 뒤에만 열린다 (ADR-0010). 그 전에는 CONTEST-409-003 이 온다. */
export function fetchContestDatasets(roundId: string): Promise<ContestDatasetsResponse> {
  return request<ContestDatasetsResponse>(`/contests/${encodeURIComponent(roundId)}/datasets`)
}
