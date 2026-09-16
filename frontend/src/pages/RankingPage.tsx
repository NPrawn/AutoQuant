import { useId, useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { RankingTable } from '../contest/RankingTable'
import { datasetKindLabels, formatDate, rankingSortLabels, regimeKinds } from '../contest/labels'
import { useContestRankings, useContests, useMyContestResult } from '../contest/queries'
import {
  defaultRankingQuery,
  isFiltered,
  setKind,
  setPage,
  setSearch,
  setSort,
  toRankingParams,
} from '../contest/rankingQuery'
import { isEntryNotFound } from '../contest/submitErrorMessage'
import type { ContestRankingSort, ContestRegimeKind } from '../contest/types'
import { useCopyStrategy } from '../strategy/queries'
import { describeCopyError } from '../strategy/translateErrorMessage'

/*
 * 목업 06 과 26. 순위는 대회 결과로만 매기므로 회차를 골라 그 회차의 순위를 본다.
 * 정렬·국면·검색·페이지는 서버가 거르고 화면은 조건만 들고 있는다.
 */
export function RankingPage() {
  const id = useId()
  const navigate = useNavigate()
  const contests = useContests()
  const copy = useCopyStrategy()

  const [query, setQuery] = useState(defaultRankingQuery)
  const [selectedRoundId, setSelectedRoundId] = useState<string | null>(null)

  const rounds = contests.data?.contests ?? []
  /* 채점이 끝난 회차만 순위가 있다. 마감이 늦은 회차가 앞에 온다. */
  const finished = rounds
    .filter((round) => round.status === 'FINISHED')
    .sort((left, right) => right.submissionEndsAt.localeCompare(left.submissionEndsAt))
  const openRound = rounds.find((round) => round.status === 'OPEN') ?? null

  const roundId = selectedRoundId ?? finished[0]?.roundId ?? null
  const round = finished.find((item) => item.roundId === roundId) ?? null

  const params = toRankingParams(query)
  const rankings = useContestRankings(roundId, params)
  const myResult = useMyContestResult(roundId ?? undefined, roundId !== null)

  const changeRound = (nextRoundId: string) => {
    setSelectedRoundId(nextRoundId)
    setQuery(defaultRankingQuery)
  }

  const copyStrategy = (strategyId: number) => {
    copy.reset()
    copy.mutate(String(strategyId), {
      onSuccess: (created) => navigate(to.strategyDetail(String(created.strategyId))),
    })
  }

  const page = rankings.data

  return (
    <section className="view active" id="view-ranking">
      <div className="page-head">
        <div>
          <span className="eyebrow">
            {round === null ? '대회 결과' : `${round.title} 채점 완료 · ${round.entryCount}팀`}
          </span>
          <h1>어떤 장세에도 버티는 전략</h1>
          <p className="sub">
            대회 결과로만 매겨지는 순위입니다. 한 장세에서 크게 번 전략보다 네 장세를 고르게 버틴 전략이 위로
            올라갑니다.
          </p>
        </div>
        {openRound && (
          <Link className="btn primary" to={to.contestDetail(openRound.roundId)}>
            {openRound.title} 참가
          </Link>
        )}
      </div>

      {contests.isPending && <LoadingState label="회차를 불러오는 중…" />}
      {contests.isError && (
        <ErrorState title="회차를 불러오지 못했습니다" error={contests.error} onRetry={() => contests.refetch()} />
      )}
      {contests.isSuccess && finished.length === 0 && (
        <EmptyState
          title="아직 채점이 끝난 회차가 없습니다"
          description="회차가 마감되고 채점이 끝나면 순위가 여기에 공개됩니다."
          actions={
            openRound && (
              <Link className="btn primary" to={to.contestDetail(openRound.roundId)}>
                진행 중인 회차 보기
              </Link>
            )
          }
        />
      )}

      {round !== null && (
        <>
          <div className="callout">
            <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8v5M12 16h.01" />
            </svg>
            <span>
              <b>채점 기준</b> — 일반장 누적 수익률 백분위가 <b>수익률 점수</b>, 상승·하락·횡보 낙폭 백분위의{' '}
              <b>평균 × 0.6 + 최저 × 0.4</b>가 안정성 점수이고 <b>종합 = 수익률 × 0.5 + 안정성 × 0.5</b>입니다. 국면
              점수는 낙폭 백분위라 높을수록 낙폭이 작습니다.
            </span>
          </div>

          <div className="rfilt">
            <label className="sr-only" htmlFor={`${id}-round`}>
              회차
            </label>
            <select id={`${id}-round`} value={round.roundId} onChange={(event) => changeRound(event.target.value)}>
              {finished.map((item) => (
                <option key={item.roundId} value={item.roundId}>
                  {item.title} · {formatDate(item.submissionEndsAt)}
                </option>
              ))}
            </select>

            <label className="sr-only" htmlFor={`${id}-sort`}>
              정렬
            </label>
            <select
              id={`${id}-sort`}
              value={query.sort}
              onChange={(event) => setQuery(setSort(query, event.target.value as ContestRankingSort))}
            >
              {Object.entries(rankingSortLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  정렬: {label}
                </option>
              ))}
            </select>

            <label className="sr-only" htmlFor={`${id}-kind`}>
              국면 강점
            </label>
            <select
              id={`${id}-kind`}
              value={query.kind ?? ''}
              onChange={(event) =>
                setQuery(setKind(query, event.target.value === '' ? null : (event.target.value as ContestRegimeKind)))
              }
            >
              <option value="">국면 강점: 전체</option>
              {regimeKinds.map((kind) => (
                <option key={kind} value={kind}>
                  국면 강점: {datasetKindLabels[kind]}
                </option>
              ))}
            </select>

            <label className="sr-only" htmlFor={`${id}-search`}>
              전략·참가자 검색
            </label>
            <input
              id={`${id}-search`}
              type="search"
              placeholder="전략 이름이나 참가자 검색"
              value={query.search}
              onChange={(event) => setQuery(setSearch(query, event.target.value))}
            />
          </div>

          {copy.isError && (
            <div style={{ marginBottom: 16 }}>
              <ErrorState
                title="복제하지 못했습니다"
                description={describeCopyError(copy.error)}
                error={copy.error}
                actions={
                  <button className="btn" type="button" onClick={() => copy.reset()}>
                    닫기
                  </button>
                }
              />
            </div>
          )}

          {rankings.isPending && <LoadingState label="순위를 불러오는 중…" />}
          {rankings.isError && (
            <ErrorState title="순위를 불러오지 못했습니다" error={rankings.error} onRetry={() => rankings.refetch()} />
          )}

          {page !== undefined && page.content.length === 0 && (
            <EmptyState
              title="조건에 맞는 전략이 없습니다"
              description={
                isFiltered(query)
                  ? `${round.title}에서 지금 조건에 맞는 전략이 없습니다. 조건을 풀거나 다른 회차를 보세요.`
                  : `${round.title}은 순위에 오른 전략이 없습니다. 참가 ${page.disqualifiedCount}팀이 모두 실격입니다.`
              }
              actions={
                isFiltered(query) && (
                  <button className="btn primary" type="button" onClick={() => setQuery(defaultRankingQuery)}>
                    필터 초기화
                  </button>
                )
              }
            />
          )}

          {page !== undefined && page.content.length > 0 && (
            <>
              <RankingTable
                roundId={round.roundId}
                rows={page.content}
                myResult={myResult.isSuccess && !isEntryNotFound(myResult.error) ? myResult.data : null}
                copyPending={copy.isPending}
                onCopy={copyStrategy}
              />
              <Pager
                page={page.page}
                totalPages={page.totalPages}
                disabled={rankings.isPlaceholderData}
                onPageChange={(next) => setQuery(setPage(query, next))}
              />
              {page.disqualifiedCount > 0 && (
                <p className="sec-sub" style={{ marginTop: 12 }}>
                  실격 {page.disqualifiedCount}팀은 순위에서 빠졌습니다. 사유는 참가자 본인의 결과 화면에서 볼 수
                  있습니다.
                </p>
              )}
            </>
          )}
        </>
      )}
    </section>
  )
}
