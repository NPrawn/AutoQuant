import { Link, useParams } from 'react-router'

import { routes, to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { ContestDatasetTable } from '../contest/ContestDatasetCards'
import { ContestMyScorePanel } from '../contest/ContestMyScorePanel'
import { ContestProgressPanel } from '../contest/ContestProgressPanel'
import { contestStatusLabels, describeDisqualification, formatScore } from '../contest/labels'
import {
  useContest,
  useContestDatasets,
  useContestProgress,
  useContestResults,
  useMyContestResult,
} from '../contest/queries'
import { isEntryNotFound } from '../contest/submitErrorMessage'
import type { ContestRankingItemResponse } from '../contest/types'

/*
 * 목업 32(채점 중), 33(결과), 35(실격). 한 회차의 결과 화면은 상태에 따라 세 모습이라
 * 페이지를 나누지 않고 진행 상황이 알려 준 상태로 갈라 그린다.
 */
export function ContestResultPage() {
  const { contestId } = useParams<{ contestId: string }>()
  const contest = useContest(contestId)
  const progress = useContestProgress(contestId)

  const status = progress.data?.status ?? null
  const finished = status === 'FINISHED'
  /* 점수가 절대값이라 채점이 끝난 제출부터 공개한다. 마감 전 순위는 잠정이다. */
  const scored = status === 'OPEN' || status === 'SCORING' || finished
  const myResult = useMyContestResult(contestId, scored)
  const results = useContestResults(contestId, scored)
  const datasets = useContestDatasets(contestId, finished)

  const title = contest.data?.title ?? '대회 결과'

  return (
    <section className="view active" id="view-contest-result">
      <Link className="back" to={routes.contests}>
        대회 목록
      </Link>

      <div className="page-head">
        <div>
          <span className="eyebrow">{status === null ? '대회' : contestStatusLabels[status]}</span>
          <h1>{title}</h1>
          <p className="sub">
            제출한 전략을 일반장·상승장·하락장·횡보장에서 모두 같은 조건으로 돌린 결과입니다. 순위는 종합 점수 순입니다.
          </p>
        </div>
        {contestId && status !== null && status !== 'FINISHED' && (
          <div className="detail-actions">
            <Link className="btn" to={to.contestDetail(contestId)}>
              회차 정보
            </Link>
          </div>
        )}
      </div>

      {progress.isPending && <LoadingState label="채점 상태를 확인하는 중…" />}
      {progress.isError && (
        <ErrorState title="채점 상태를 불러오지 못했습니다" error={progress.error} onRetry={() => progress.refetch()} />
      )}

      {progress.isSuccess && status === 'SCHEDULED' && (
        <EmptyState title="아직 채점 전입니다" description="제출이 시작되지 않은 회차입니다." />
      )}
      {progress.isSuccess && status === 'SCORING' && progress.data && <ContestProgressPanel progress={progress.data} />}

      {scored && (
        <>
          {myResult.isPending && <LoadingState label="내 결과를 불러오는 중…" />}
          {myResult.isError && isEntryNotFound(myResult.error) && (
            <EmptyState title="이 회차에 제출하지 않았습니다" description="아래에서 다른 참가자의 순위를 볼 수 있습니다." />
          )}
          {myResult.isError && !isEntryNotFound(myResult.error) && (
            <ErrorState title="내 결과를 불러오지 못했습니다" error={myResult.error} onRetry={() => myResult.refetch()} />
          )}
          {myResult.isSuccess && <ContestMyScorePanel result={myResult.data} provisional={!finished} />}

          <div className="sec-head" style={{ margin: '24px 0 12px' }}>
            <div>
              <div className="sec-title">순위{finished ? '' : ' (잠정)'}</div>
              <div className="sec-sub">
                {finished
                  ? '실격 제출은 순위 뒤에 사유와 함께 표시됩니다'
                  : '지금까지 채점된 제출끼리의 순위입니다. 마감 때 확정됩니다'}
              </div>
            </div>
          </div>
          {results.isPending && <LoadingState label="순위를 불러오는 중…" />}
          {results.isError && (
            <ErrorState title="순위를 불러오지 못했습니다" error={results.error} onRetry={() => results.refetch()} />
          )}
          {results.isSuccess && results.data.rankings.length === 0 && (
            <EmptyState title="참가한 전략이 없습니다" description="제출이 없어 순위를 매기지 않았습니다." />
          )}
          {results.isSuccess && results.data.rankings.length > 0 && <RankingTable rankings={results.data.rankings} />}

          {datasets.isSuccess && datasets.data.datasets.length > 0 && (
            <>
              <div className="sec-head" style={{ margin: '32px 0 12px' }}>
                <div>
                  <div className="sec-title">채점용 데이터</div>
                  <div className="sec-sub">마감 후 공개됩니다. 모든 참가자가 같은 데이터로 채점됐습니다.</div>
                </div>
              </div>
              <ContestDatasetTable datasets={datasets.data.datasets} />
            </>
          )}
        </>
      )}
    </section>
  )
}

function RankingTable({ rankings }: { rankings: ContestRankingItemResponse[] }) {
  return (
    <div className="card">
      <div className="table-scroll" tabIndex={0} role="region" aria-label="회차 결과 표">
        <table>
          <thead>
            <tr>
              <th scope="col" style={{ width: 72 }}>순위</th>
              <th scope="col">전략</th>
              <th scope="col" className="r">종합</th>
              <th scope="col" className="r">수익률</th>
              <th scope="col" className="r">안정성</th>
              <th scope="col">판정</th>
            </tr>
          </thead>
          <tbody>
            {rankings.map((row, index) => {
              const disqualification = describeDisqualification(row.disqualificationReason, row.disqualifiedKind)
              return (
                <tr key={`${row.nickname}-${row.strategyName}-${index}`}>
                  <td className="mono">{row.ranking ?? '—'}</td>
                  <td className="strategy-name-cell">
                    <b>{row.strategyName}</b>
                    <small>{row.nickname}</small>
                  </td>
                  <td className="r mono">{formatScore(row.totalScore)}</td>
                  <td className="r mono">{formatScore(row.returnScore)}</td>
                  <td className="r mono">{formatScore(row.stabilityScore)}</td>
                  <td>{disqualification === null ? <span className="chip mute">통과</span> : <span className="chip crit">{disqualification}</span>}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
