import type { ContestRankingSort, ContestRegimeKind } from './types'

/*
 * 랭킹 표의 조회 조건. 정렬·국면·검색은 서버가 거르므로 화면은 조건만 들고 있는다.
 * 조건을 바꾸면 결과 수가 달라져 보던 페이지가 의미를 잃는다. 그래서 첫 페이지로 되돌린다.
 */
export type RankingQuery = {
  sort: ContestRankingSort
  kind: ContestRegimeKind | null
  search: string
  page: number
}

export const rankingPageSize = 20

export const defaultRankingQuery: RankingQuery = { sort: 'TOTAL', kind: null, search: '', page: 0 }

export function setSort(query: RankingQuery, sort: ContestRankingSort): RankingQuery {
  return { ...query, sort, page: 0 }
}

export function setKind(query: RankingQuery, kind: ContestRegimeKind | null): RankingQuery {
  return { ...query, kind, page: 0 }
}

export function setSearch(query: RankingQuery, search: string): RankingQuery {
  return { ...query, search, page: 0 }
}

export function setPage(query: RankingQuery, page: number): RankingQuery {
  return { ...query, page: Math.max(page, 0) }
}

/* 빈 결과 화면이 "필터를 풀어 보라"고 안내할지 판단한다. */
export function isFiltered(query: RankingQuery): boolean {
  return query.kind !== null || query.search.trim() !== ''
}

/* 서버에 넘길 조건. 기본값과 빈 검색어는 빼서 쿼리 문자열과 캐시 키를 짧게 만든다. */
export function toRankingParams(query: RankingQuery): Record<string, string> {
  const params: Record<string, string> = { page: String(query.page), size: String(rankingPageSize) }
  if (query.sort !== 'TOTAL') {
    params.sort = query.sort
  }
  if (query.kind !== null) {
    params.kind = query.kind
  }
  const search = query.search.trim()
  if (search !== '') {
    params.query = search
  }
  return params
}
