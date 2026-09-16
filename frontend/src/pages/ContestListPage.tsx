import { Link } from 'react-router'

import { to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { useNow } from '../contest/clock'
import { contestStatusLabels, formatDate, formatDateTime, formatRemaining } from '../contest/labels'
import { useContests } from '../contest/queries'
import type { ContestRoundItemResponse } from '../contest/types'

/*
 * 목업 30. 진행 중(또는 채점 중) 회차 하나를 위에 크게 놓고 예정·지난 회차는 표로 본다.
 * 목업의 "이번 회차 장세(연습용)" 영역은 채점용 데이터만 두기로 해서 뺐다 (대회 기능 설계 9절).
 */
export function ContestListPage() {
  const contests = useContests()
  const rounds = contests.data?.contests ?? []
  const live = rounds.find((round) => round.status === 'OPEN') ?? rounds.find((round) => round.status === 'SCORING') ?? null
  const upcoming = rounds.filter((round) => round.status === 'SCHEDULED')
  const past = rounds.filter((round) => round.status === 'FINISHED')

  return (
    <section className="view active" id="view-contest">
      <div className="page-head">
        <div>
          <span className="eyebrow">대회</span>
          <h1>같은 장세 4개, 같은 전략</h1>
          <p className="sub">
            제출한 전략 하나를 일반장·상승장·하락장·횡보장에서 모두 같은 조건으로 돌립니다. 한 장세에만 맞춘 전략은 여기서
            걸러집니다.
          </p>
        </div>
      </div>

      {contests.isPending && <LoadingState label="대회 회차를 불러오는 중…" />}
      {contests.isError && (
        <ErrorState title="대회 회차를 불러오지 못했습니다" error={contests.error} onRetry={() => contests.refetch()} />
      )}
      {contests.isSuccess && rounds.length === 0 && (
        <EmptyState title="아직 열린 대회가 없습니다" description="회차가 열리면 이 화면에서 제출할 수 있습니다." />
      )}

      {live && <LiveRoundCard round={live} />}

      <div className="callout" style={{ marginTop: 24 }}>
        <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v5M12 16h.01" />
        </svg>
        <span>
          <b>채점 기준</b> — 일반장 누적 수익률 백분위가 <b>수익률 점수</b>, 장세 3벌의 최대 낙폭 백분위가{' '}
          <b>안정성 점수</b>이고 <b>종합 = 수익률 × 0.5 + 안정성 × 0.5</b>입니다. 한 장세라도 수익률 -30% 미만이거나 최대
          낙폭 50%를 넘으면 순위에서 제외됩니다.
        </span>
      </div>

      {upcoming.length > 0 && (
        <>
          <div className="sec-head" style={{ margin: '32px 0 12px' }}>
            <div>
              <div className="sec-title">예정된 회차</div>
            </div>
          </div>
          <div className="card">
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>이름</th>
                    <th className="r">제출 시작</th>
                    <th className="r">마감</th>
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

      {past.length > 0 && (
        <>
          <div className="sec-head" style={{ margin: '32px 0 12px' }}>
            <div>
              <div className="sec-title">지난 회차</div>
              <div className="sec-sub">회차마다 장세가 다르므로 회차 간 점수를 그대로 비교하지 않습니다.</div>
            </div>
          </div>
          <div className="card">
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>이름</th>
                    <th className="r">참가</th>
                    <th>내 제출</th>
                    <th className="r">마감</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {past.map((round) => (
                    <tr key={round.roundId}>
                      <td>{round.title}</td>
                      <td className="r mono">{round.entryCount}</td>
                      <td className={round.mySubmission ? 'strategy-name-cell' : 'muted-cell'}>
                        {round.mySubmission?.strategyName ?? '미참가'}
                      </td>
                      <td className="r mono">{formatDate(round.submissionEndsAt)}</td>
                      <td style={{ textAlign: 'right' }}>
                        <Link className="rank-action" to={to.contestResult(round.roundId)}>
                          결과 보기
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
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
            <span>참가</span>
            <strong>{round.entryCount}</strong>
          </div>
          <div>
            <span>내 제출</span>
            <strong className="txt">{round.mySubmission?.strategyName ?? '없음'}</strong>
          </div>
          <div>
            <span>남은 제출</span>
            <strong>{round.mySubmission ? round.mySubmission.remainingSubmissions : '—'}</strong>
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
