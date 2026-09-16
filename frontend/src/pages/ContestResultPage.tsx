import { Link, useParams } from 'react-router'

import { routes, to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { ContestDatasetTable, ContestOutcomeGrid } from '../contest/ContestDatasetCards'
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
import type { ContestMyResultResponse, ContestRankingItemResponse } from '../contest/types'

/*
 * 목업 32(채점 중), 33(결과), 35(실격). 한 회차의 결과 화면은 상태에 따라 세 모습이라
 * 페이지를 나누지 않고 진행 상황이 알려 준 상태로 갈라 그린다.
 */
export function ContestResultPage() {
  const { contestId } = useParams<{ contestId: string }>()
  const contest = useContest(contestId)
  const progress = useContestProgress(contestId)

  const finished = progress.data?.status === 'FINISHED'
  const myResult = useMyContestResult(contestId, finished)
  const results = useContestResults(contestId, finished)
  const datasets = useContestDatasets(contestId, finished)

  const title = contest.data?.title ?? '대회 결과'
  const status = progress.data?.status ?? null

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
      {progress.isSuccess && status === 'OPEN' && (
        <EmptyState
          title="아직 제출 기간입니다"
          description="마감 후 채점이 시작되고, 끝나면 이 화면에 순위가 공개됩니다."
        />
      )}
      {progress.isSuccess && status === 'SCORING' && progress.data && <ContestProgressPanel progress={progress.data} />}

      {finished && (
        <>
          {myResult.isPending && <LoadingState label="내 결과를 불러오는 중…" />}
          {myResult.isError && isEntryNotFound(myResult.error) && (
            <EmptyState title="이 회차에 제출하지 않았습니다" description="아래에서 다른 참가자의 순위를 볼 수 있습니다." />
          )}
          {myResult.isError && !isEntryNotFound(myResult.error) && (
            <ErrorState title="내 결과를 불러오지 못했습니다" error={myResult.error} onRetry={() => myResult.refetch()} />
          )}
          {myResult.isSuccess && <MyResultSection result={myResult.data} />}

          <div className="sec-head" style={{ margin: '24px 0 12px' }}>
            <div>
              <div className="sec-title">순위</div>
              <div className="sec-sub">실격 제출은 순위 뒤에 사유와 함께 표시됩니다</div>
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

/* 목업 33 과 35. 실격이면 점수 자리에 제외를 적고 사유를 먼저 보여 준다. */
function MyResultSection({ result }: { result: ContestMyResultResponse }) {
  const disqualification = describeDisqualification(result.disqualificationReason, result.disqualifiedKind)

  return (
    <>
      <section className="card score-hero card--lead">
        <div className="score-num">
          <span>종합 점수</span>
          {disqualification === null ? (
            <strong>{formatScore(result.totalScore)}</strong>
          ) : (
            <strong className="txt" style={{ color: 'var(--crit)' }}>
              제외
            </strong>
          )}
          <small>
            {disqualification === null
              ? `${result.entryCount}팀 중 ${result.ranking ?? '—'}위`
              : '순위 산정에서 빠짐'}
          </small>
        </div>
        <div className="vr" />
        <div className="score-meta">
          <div>
            <span>수익률 점수</span>
            <strong>{formatScore(result.returnScore)}</strong>
            <em>일반장 누적 수익률 백분위</em>
          </div>
          <div>
            <span>안정성 점수</span>
            <strong>{formatScore(result.stabilityScore)}</strong>
            <em>장세 3벌 낙폭 백분위</em>
          </div>
          <div>
            <span>1위 총점</span>
            <strong>{formatScore(result.topTotalScore)}</strong>
            <em>같은 회차</em>
          </div>
          <div>
            <span>실격 판정</span>
            <strong className="txt">{disqualification ?? '없음'}</strong>
            <em>{disqualification === null ? '모든 기준 통과' : '이번 회차 제외'}</em>
          </div>
        </div>
      </section>

      {disqualification !== null && (
        <div className="callout warn" style={{ marginTop: 20 }}>
          <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 9v4M12 17h.01" />
            <circle cx="12" cy="12" r="9" />
          </svg>
          <span>
            <b>{disqualification}</b> 판정으로 이번 회차 순위에서 빠졌습니다. 아래 장세별 성적에서 어느 구간이 기준을 넘었는지
            확인하고 전략을 고친 뒤 다음 회차에 다시 제출할 수 있습니다.
          </span>
        </div>
      )}

      <div className="sec-head" style={{ margin: '24px 0 12px' }}>
        <div>
          <div className="sec-title">장세별 성적</div>
          <div className="sec-sub">채점용 데이터 · 모든 참가자와 같은 조건</div>
        </div>
        <span className="chip mute">채점용</span>
      </div>
      <ContestOutcomeGrid outcomes={result.datasets} />

      <div className="card pad" style={{ marginTop: 16 }}>
        <div className="sec-head">
          <div>
            <div className="sec-title">낙폭 백분위</div>
            <div className="sec-sub">낮은 낙폭이 높은 백분위다. 안정성 점수는 평균 × 0.6 + 최저 × 0.4</div>
          </div>
        </div>
        <PercentileRow label="상승장" tone="bull" value={result.bullMddPercentile} />
        <PercentileRow label="하락장" tone="bear" value={result.bearMddPercentile} />
        <PercentileRow label="횡보장" tone="side" value={result.sidewaysMddPercentile} />
      </div>
    </>
  )
}

function PercentileRow({ label, tone, value }: { label: string; tone: 'bull' | 'bear' | 'side'; value: number | null }) {
  const width = value === null ? 0 : Math.min(Math.max(value, 0), 100)
  return (
    <div className={`pct ${tone}`}>
      <span>{label}</span>
      <div className="pct-track">
        <i style={{ width: `${width}%` }} />
      </div>
      <span className="pct-val">{value === null ? '—' : value.toFixed(0)}</span>
    </div>
  )
}

function RankingTable({ rankings }: { rankings: ContestRankingItemResponse[] }) {
  return (
    <div className="card">
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th style={{ width: 72 }}>순위</th>
              <th>전략</th>
              <th className="r">종합</th>
              <th className="r">수익률</th>
              <th className="r">안정성</th>
              <th>판정</th>
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
