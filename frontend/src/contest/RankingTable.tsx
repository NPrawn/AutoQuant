import { Link } from 'react-router'

import { to } from '../app/routes'
import { StrategyName } from '../strategy/StrategyName'
import { formatPercent, formatRank, formatScore } from './labels'
import { awaitingScore, type ContestMyResultResponse, type ContestRankingRowResponse } from './types'

type RankingTableProps = {
  roundId: string
  rows: ContestRankingRowResponse[]
  /* 내 제출이 이 페이지에 없으면 표 아래에 고정해 보여 준다. */
  myResult: ContestMyResultResponse | null
  copyPending: boolean
  onCopy: (strategyId: number) => void
}

/*
 * 목업 06 의 순위표. 상위 3위는 .rnum.top 으로 구분하고 내 행은 .rank-me 로 아래에 붙는다.
 *
 * 점수 대신 읽히는 숫자를 보여 준다. 수익률은 원금 대비 %, 낙폭은 일반장 최대 낙폭이다. 국면 점수
 * 3개는 안정성 점수가 이미 요약하므로 열에서 뺐다. 종합 점수와 수익률만 강조색을 쓴다.
 */
export function RankingTable({ roundId, rows, myResult, copyPending, onCopy }: RankingTableProps) {
  const mineOnPage = rows.some((row) => row.mine)

  return (
    <div className="card">
      <div className="table-scroll" tabIndex={0} role="region" aria-label="순위표 표">
        <table>
          <thead>
            <tr>
              <th scope="col" style={{ width: 58 }}>순위</th>
              <th scope="col">전략</th>
              <th scope="col" className="r">종합 점수</th>
              <th scope="col" className="r">수익률</th>
              <th scope="col" className="r">안정성</th>
              <th scope="col" className="r">MDD</th>
              <th scope="col"><span className="sr-only">작업</span></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={`${row.strategyId}-${row.ranking}`} className={row.mine ? 'rank-me' : undefined}>
                <td>
                  <span className={row.ranking <= 3 ? 'rnum top' : 'rnum'}>{formatRank(row.ranking)}</span>
                </td>
                <td className="strategy-name-cell">
                  <b>
                    <StrategyName label={{ name: row.strategyName, version: row.versionNo }} />
                  </b>
                  <small>{row.mine ? `${row.nickname} · 내 전략` : row.nickname}</small>
                </td>
                <td className="r">
                  <b className="mono score-lead">{formatScore(row.totalScore)}</b>
                </td>
                <td className="r">
                  <ReturnCell value={row.generalReturn} />
                </td>
                <td className="r mono">{formatScore(row.stabilityScore)}</td>
                <td className="r mono">{formatPercent(row.generalMdd)}</td>
                <td style={{ textAlign: 'right' }}>
                  <RowAction roundId={roundId} row={row} copyPending={copyPending} onCopy={onCopy} />
                </td>
              </tr>
            ))}
            {!mineOnPage && myResult && <MyRow roundId={roundId} result={myResult} />}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* 원금 대비 수익률. 오르면 --up, 내리면 --down 이라 색 계약을 그대로 따른다. */
function ReturnCell({ value }: { value: number | null }) {
  if (value === null) {
    return <span className="mono">—</span>
  }
  return <span className={value < 0 ? 'mono updown down' : 'mono updown up'}>{formatPercent(value)}</span>
}

/* 복제는 공개 전략에만 건다. 내 전략은 복제 대신 결과로 간다. */
function RowAction({ roundId, row, copyPending, onCopy }: {
  roundId: string
  row: ContestRankingRowResponse
  copyPending: boolean
  onCopy: (strategyId: number) => void
}) {
  if (row.mine) {
    return (
      <Link className="rank-action" to={to.contestResult(roundId)}>
        결과 보기
      </Link>
    )
  }
  if (!row.published) {
    return <span className="muted-cell">비공개</span>
  }
  return (
    <button className="rank-action" type="button" disabled={copyPending} onClick={() => onCopy(row.strategyId)}>
      복제
    </button>
  )
}

/*
 * 표 아래에 붙는 내 성적. 순위 페이지를 넘겨도 계속 보인다. 순위가 비는 경우는 실격과 채점 전 둘이라
 * 순위만 보고 실격이라 적으면 채점을 기다리는 제출을 실격으로 오해하게 한다. 실격 사유로 가른다.
 */
function myRowNote(result: ContestMyResultResponse): string {
  if (result.disqualificationReason !== null) {
    return '내 전략 · 실격'
  }
  return awaitingScore(result) ? '내 전략 · 채점 중' : '내 전략'
}

function MyRow({ roundId, result }: { roundId: string; result: ContestMyResultResponse }) {
  /* 내 성적 응답에는 수익률·낙폭이 데이터셋별로만 있다. 표가 쓰는 값은 일반장 것이다. */
  const general = result.datasets.find((dataset) => dataset.kind === 'GENERAL')

  return (
    <tr className="rank-me">
      <td>
        <span className="rnum">{result.ranking === null ? '—' : formatRank(result.ranking)}</span>
      </td>
      <td className="strategy-name-cell">
        <b>{result.strategyName}</b>
        <small>{myRowNote(result)}</small>
      </td>
      <td className="r">
        <b className="mono score-lead">{formatScore(result.totalScore)}</b>
      </td>
      <td className="r">
        <ReturnCell value={general?.cumulativeReturn ?? null} />
      </td>
      <td className="r mono">{formatScore(result.stabilityScore)}</td>
      <td className="r mono">{formatPercent(general?.maximumDrawdown ?? null)}</td>
      <td style={{ textAlign: 'right' }}>
        <Link className="rank-action" to={to.contestResult(roundId)}>
          결과 보기
        </Link>
      </td>
    </tr>
  )
}
