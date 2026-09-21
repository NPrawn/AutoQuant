import { Link } from 'react-router'

import { to } from '../app/routes'
import { formatPercentile, formatRank, formatScore } from './labels'
import type { ContestMyResultResponse, ContestRankingRowResponse } from './types'

type RankingTableProps = {
  roundId: string
  rows: ContestRankingRowResponse[]
  /* 내 제출이 이 페이지에 없으면 표 아래에 고정해 보여 준다. */
  myResult: ContestMyResultResponse | null
  copyPending: boolean
  onCopy: (strategyId: number) => void
}

/* 목업 06 의 순위표. 상위 3위는 .rnum.top 으로 구분하고 내 행은 .rank-me 로 아래에 붙는다. */
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
              <th scope="col" className="r">상승</th>
              <th scope="col" className="r">하락</th>
              <th scope="col" className="r">횡보</th>
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
                  <b>{row.strategyName}</b>
                  <small>{row.mine ? `${row.nickname} · 내 전략` : row.nickname}</small>
                </td>
                <td className="r">
                  <b className="mono">{formatScore(row.totalScore)}</b>
                </td>
                <td className="r mono">{formatPercentile(row.returnScore)}</td>
                <td className="r mono">{formatPercentile(row.stabilityScore)}</td>
                <td className="r">
                  <span className="mono updown up">{formatPercentile(row.bullRegimeScore)}</span>
                </td>
                <td className="r">
                  <span className="mono updown down">{formatPercentile(row.bearRegimeScore)}</span>
                </td>
                <td className="r mono">{formatPercentile(row.sidewaysRegimeScore)}</td>
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
 * 표 아래에 붙는 내 성적. 순위 페이지를 넘겨도 계속 보인다. 실격이면 순위와 점수가 비어 있어
 * 그 자리에 실격을 적는다.
 */
function MyRow({ roundId, result }: { roundId: string; result: ContestMyResultResponse }) {
  return (
    <tr className="rank-me">
      <td>
        <span className="rnum">{result.ranking === null ? '—' : formatRank(result.ranking)}</span>
      </td>
      <td className="strategy-name-cell">
        <b>{result.strategyName}</b>
        <small>{result.ranking === null ? '내 전략 · 실격' : '내 전략'}</small>
      </td>
      <td className="r">
        <b className="mono">{formatScore(result.totalScore)}</b>
      </td>
      <td className="r mono">{formatPercentile(result.returnScore)}</td>
      <td className="r mono">{formatPercentile(result.stabilityScore)}</td>
      <td className="r">
        <span className="mono updown up">{formatPercentile(result.bullRegimeScore)}</span>
      </td>
      <td className="r">
        <span className="mono updown down">{formatPercentile(result.bearRegimeScore)}</span>
      </td>
      <td className="r mono">{formatPercentile(result.sidewaysRegimeScore)}</td>
      <td style={{ textAlign: 'right' }}>
        <Link className="rank-action" to={to.contestResult(roundId)}>
          결과 보기
        </Link>
      </td>
    </tr>
  )
}
