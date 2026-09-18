import type { MouseEvent } from 'react'

import { formatDate, formatDateTime, formatKrw, statusOf } from './labels'
import type { BacktestRun } from './types'

type RunHistoryTableProps = {
  runs: BacktestRun[]
  /* 목록 응답에는 전략 이름이 없어 전략 목록으로 맞춘다. 지워진 전략은 식별자로 보인다. */
  strategyNames: ReadonlyMap<string, string>
  showStrategy?: boolean
  onOpen: (backtestId: string) => void
}

function strategyNameOf(strategyNames: ReadonlyMap<string, string>, strategyId: string): string {
  return strategyNames.get(strategyId) ?? `전략 #${strategyId}`
}

/* 실행 이력 표. 백테스트 화면과 전략 성과 탭이 같은 표를 쓰고 전략 열만 켜고 끈다. */
export function RunHistoryTable({ runs, strategyNames, showStrategy = true, onOpen }: RunHistoryTableProps) {
  return (
    <div className="card">
      <div className="table-scroll" tabIndex={0} role="region" aria-label="실행 이력 표">
        <table>
          <thead>
            <tr>
              <th scope="col">상태</th>
              {showStrategy && <th scope="col">전략</th>}
              <th scope="col">평가 기간</th>
              <th scope="col" className="r">초기자산</th>
              <th scope="col">접수 시각</th>
              <th scope="col" style={{ textAlign: 'right' }}>결과</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => (
              <RunRow key={run.id} run={run} strategyName={showStrategy ? strategyNameOf(strategyNames, run.strategyId) : null} onOpen={() => onOpen(run.id)} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

type RunRowProps = {
  run: BacktestRun
  strategyName: string | null
  onOpen: () => void
}

/* 행 전체가 클릭 영역이지만 키보드 사용자는 마지막 열의 버튼으로 들어간다. */
function RunRow({ run, strategyName, onOpen }: RunRowProps) {
  const status = statusOf(run.status)
  const handleRowClick = (event: MouseEvent<HTMLTableRowElement>) => {
    if ((event.target as HTMLElement).closest('a, button')) {
      return
    }
    onOpen()
  }

  return (
    <tr className="click" onClick={handleRowClick}>
      <td>
        <span className={status.chip}>{status.label}</span>
      </td>
      {strategyName !== null && (
        <td>
          <b>{strategyName}</b>
        </td>
      )}
      <td className="mono muted-cell">
        {formatDate(run.periodStart)} ~ {formatDate(run.periodEnd)}
      </td>
      <td className="r mono">{formatKrw(run.initialCapital)}</td>
      <td className="muted-cell">{formatDateTime(run.queuedAt)}</td>
      <td style={{ textAlign: 'right' }}>
        <button className="rank-action" type="button" onClick={onOpen}>
          {run.status === 'COMPLETED' ? '결과 보기' : run.status === 'FAILED' ? '자세히' : '진행 보기'}
        </button>
      </td>
    </tr>
  )
}
