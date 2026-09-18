import assert from 'node:assert/strict'
import { test } from 'node:test'
import { formatPercentile, formatRank } from '../src/contest/labels.ts'
import {
  defaultRankingQuery,
  isFiltered,
  rankingPageSize,
  setKind,
  setPage,
  setSearch,
  setSort,
  toRankingParams,
} from '../src/contest/rankingQuery.ts'

test('조건을 바꾸면 첫 페이지로 돌아가고 인수는 그대로 둔다', () => {
  const third = setPage(defaultRankingQuery, 2)
  const snapshot = JSON.stringify(third)

  assert.equal(setSort(third, 'STABILITY').page, 0)
  assert.equal(setKind(third, 'BEAR').page, 0)
  assert.equal(setSearch(third, '모멘텀').page, 0)
  assert.equal(setPage(third, 3).page, 3)
  assert.equal(JSON.stringify(third), snapshot)
})

test('페이지는 0 아래로 내려가지 않는다', () => {
  assert.equal(setPage(defaultRankingQuery, -1).page, 0)
})

test('필터를 걸었는지로 빈 화면의 안내가 갈린다', () => {
  assert.equal(isFiltered(defaultRankingQuery), false)
  assert.equal(isFiltered(setSort(defaultRankingQuery, 'STABILITY')), false)
  assert.equal(isFiltered(setKind(defaultRankingQuery, 'BULL')), true)
  assert.equal(isFiltered(setSearch(defaultRankingQuery, '  ')), false)
  assert.equal(isFiltered(setSearch(defaultRankingQuery, ' 모멘텀 ')), true)
})

test('서버 조건에는 기본값과 빈 검색어를 싣지 않는다', () => {
  assert.deepEqual(toRankingParams(defaultRankingQuery), { page: '0', size: String(rankingPageSize) })

  const query = setSearch(setKind(setSort(defaultRankingQuery, 'STABILITY'), 'BEAR'), '  모멘텀  ')
  assert.deepEqual(toRankingParams(setPage(query, 2)), {
    page: '2',
    size: String(rankingPageSize),
    sort: 'STABILITY',
    kind: 'BEAR',
    query: '모멘텀',
  })
})

test('순위는 두 자리로, 백분위는 정수로 읽는다', () => {
  assert.equal(formatRank(1), '01')
  assert.equal(formatRank(12), '12')
  assert.equal(formatPercentile(66.667), '67')
  assert.equal(formatPercentile(null), '—')
})
