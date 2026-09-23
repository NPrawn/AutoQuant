import { Link } from 'react-router'

import { to } from '../app/routes'
import { StrategyName } from '../strategy/StrategyName'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { useNow } from '../contest/clock'
import {
  contestStatusLabels,
  formatDate,
  formatDateTime,
  formatRemaining,
  formatSignedScore,
  rankTone,
  scoreTone,
} from '../contest/labels'
import { useContests, useMyContestEntries } from '../contest/queries'
import type { ContestMyEntryResponse, ContestRoundItemResponse } from '../contest/types'

/*
 * 목업 30. 진행 중(또는 채점 중) 회차 하나를 위에 크게 놓고 예정·지난 회차는 표로 본다.
 * 목업의 "이번 회차 장세(연습용)" 영역은 채점용 데이터만 두기로 해서 뺐다 (대회 기능 설계 9절).
 */
export function ContestListPage() {
  const contests = useContests()
  const rounds = contests.data?.contests ?? []
  const live = rounds.find((round) => round.status === 'OPEN') ?? rounds.find((round) => round.status === 'SCORING') ?? null
  const upcoming = rounds.filter((round) => round.status === 'SCHEDULED')

  return (
    <section className="view active" id="view-contest">
      <div className="page-head">
        <div>
          <span className="eyebrow">대회</span>
          <h1>같은 장세 4개, 같은 전략</h1>
        </div>
      </div>

      {contests.isPending && <LoadingState label="대회 회차를 불러오는 중…" />}
      {contests.isError && (
        <ErrorState title="대회 회차를 불러오지 못했습니다" error={contests.error} onRetry={() => contests.refetch()} />
      )}
      {/* 회차는 끝나도 다음 회차가 이어 열리지 않는다. 준비되면 새로 연다. 그동안은 그 상태를 그대로 말한다. */}
      {contests.isSuccess && live === null && upcoming.length === 0 && (
        <EmptyState title="현재 열린 대회가 없습니다" />
      )}

      {live && <LiveRoundCard round={live} />}

      {upcoming.length > 0 && (
        <>
          <div className="sec-head" style={{ margin: '32px 0 12px' }}>
            <div>
              <div className="sec-title">예정된 회차</div>
            </div>
          </div>
          <div className="card">
            <div className="table-scroll" tabIndex={0} role="region" aria-label="대회 회차 표">
              <table>
                <thead>
                  <tr>
                    <th scope="col">이름</th>
                    <th scope="col" className="r">제출 시작</th>
                    <th scope="col" className="r">마감</th>
                  </tr>
                </thead>
                <tbody>
                  {upcoming.map((round) => (
                    <tr key={round.roundId}>
                      <td>{round.title}</td>
                      <td className="r mono">{formatDate(round.submissionStartsAt)}</td>
                      <td className="r mono">{formatDate(round.submissionEndsAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      <MyEntries />
    </section>
  )
}


/* 목업 30 의 .ct-live. 제출 중이면 마감까지 남은 시간을, 채점 중이면 결과로 가는 길을 보여 준다. */
function LiveRoundCard({ round }: { round: ContestRoundItemResponse }) {
  const now = useNow()
  const remaining = formatRemaining(round.submissionEndsAt, now)
  const scoring = round.status === 'SCORING'

  return (
    <section className="card card--lead ct-live">
      <div className="ct-live-main">
        <div className="ct-round">
          <i />
          {contestStatusLabels[round.status]}
        </div>
        <h2>{round.title}</h2>
        <p>
          {scoring
            ? '마감된 전략을 채점용 장세에서 돌리고 있습니다. 끝나면 순위가 공개됩니다.'
            : '마감까지 전략을 자유롭게 교체할 수 있습니다. 마감 후 공개되는 채점용 장세로 최종 순위를 냅니다.'}
        </p>
        <div className="ct-facts">
          <div>
            <span>제출</span>
            <strong>{round.entryCount}</strong>
          </div>
          <div>
            <span>내 제출</span>
            <strong className="txt">{round.mySubmission?.strategyName ?? '없음'}</strong>
          </div>
          <div>
            <span>채점 장세</span>
            <strong className="txt">일반 · 상승 · 하락 · 횡보</strong>
          </div>
        </div>
      </div>
      <div className="ct-live-side">
        <div className="ct-clock">
          <span>{scoring ? '채점 중' : '제출 마감'}</span>
          <strong>{remaining ?? '마감'}</strong>
          <small>{formatDateTime(round.submissionEndsAt)}</small>
        </div>
        <Link className="btn primary" to={to.contestDetail(round.roundId)}>
          {scoring ? '회차 정보 보기' : round.mySubmission ? '제출 전략 바꾸기' : '전략 제출하기'}
        </Link>
        {scoring && (
          <Link className="btn" to={to.contestResult(round.roundId)}>
            채점 현황 보기
          </Link>
        )}
      </div>
    </section>
  )
}

/*
 * 내 제출 목록. 제출마다 한 줄이라 같은 회차가 여러 번 나온다. 지난 회차 표를 대신한다 —
 * 회차 자체보다 내가 무엇을 언제 내서 몇 점을 받았는지가 이 화면에서 볼 값이다.
 */
function MyEntries() {
  const entries = useMyContestEntries()

  if (entries.isPending) {
    return <LoadingState label="내 제출을 불러오는 중" />
  }
  if (entries.isError) {
    return <ErrorState title="내 제출을 불러오지 못했습니다" onRetry={() => entries.refetch()} />
  }

  const rows = entries.data?.entries ?? []

  return (
    <>
      <div className="sec-head" style={{ margin: '32px 0 12px' }}>
        <div>
          <div className="sec-title">내 제출</div>
        </div>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="아직 제출한 전략이 없습니다" />
      ) : (
        <div className="card">
          <div className="table-scroll" tabIndex={0} role="region" aria-label="내 제출 표">
            {/* 컬럼 폭을 고정하고 이름만 왼쪽, 나머지는 가운데다. 폭을 흘려 두면 전략과 제출 사이가 비어 보인다. */}
            <table className="my-entries">
              <thead>
                <tr>
                  <th scope="col">회차</th>
                  <th scope="col">전략</th>
                  <th scope="col">제출</th>
                  <th scope="col">종합 점수</th>
                  <th scope="col">순위</th>
                  <th scope="col"><span className="sr-only">작업</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((entry) => (
                  <tr key={`${entry.roundId}-${entry.submittedAt}`}>
                    <td>
                      {entry.roundTitle}
                      <small className={entry.roundStatus === 'OPEN' ? 'entry-status live' : 'entry-status'}>
                        {entryStatusLabel(entry)}
                      </small>
                    </td>
                    <td className="strategy-name-cell">
                      <b>
                        <StrategyName label={{ name: entry.strategyName, version: entry.versionNo }} />
                      </b>
                    </td>
                    <td className="mono num-soft">{formatDateTime(entry.submittedAt)}</td>
                    <td>
                      <b className={scoreTone(entry.totalScore)}>{formatSignedScore(entry.totalScore)}</b>
                    </td>
                    <td>
                      <span className={rankTone(entry.ranking)}>{entryRankLabel(entry)}</span>
                    </td>
                    <td>
                      <Link className="rank-action" to={to.contestResult(entry.roundId)}>
                        결과 보기
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  )
}

/* 순위가 비는 경우는 채점 전과 실격 둘이다. 사유로 갈라야 채점을 기다리는 제출을 실격으로 오해하지 않는다. */
function entryRankLabel(entry: ContestMyEntryResponse): string {
  if (entry.disqualificationReason !== null) {
    return '실격'
  }
  return entry.ranking === null ? '채점 중' : `${entry.ranking}위`
}

/* 마감 전 회차는 "진행 중" 이다. "제출 중 · 잠정" 은 무슨 뜻인지 바로 읽히지 않았다. 다른 화면의 상태 문구는 그대로다. */
function entryStatusLabel(entry: ContestMyEntryResponse): string {
  return entry.roundStatus === 'OPEN' ? '진행 중' : contestStatusLabels[entry.roundStatus]
}
