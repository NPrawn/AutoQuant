import type { MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router'

import { routes, to } from '../app/routes'
import { latestCompletedRun, runsForStrategy } from '../backtest/history'
import { blank, formatDrawdown, formatRate, trendClass } from '../backtest/labels'
import { useBacktestHistory, useBacktestResult } from '../backtest/queries'
import type { BacktestRun } from '../backtest/types'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import type { BacktestEntryState } from '../strategy/backtestEntry'
import { useStrategies } from '../strategy/queries'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'

/* sv-SE 로케일은 로컬 시간 기준 YYYY-MM-DD 를 준다. 목업의 날짜 표기와 같다. */
const dateFormat = new Intl.DateTimeFormat('sv-SE')

/*
 * 내 전략 목록(목업 05·23). 상태·수익률·낙폭은 그 전략의 최근 완료 백테스트에서 채운다.
 * 이력이 없으면 초안과 빈 값이다. 이력 전체는 한 번 받아 행마다 거른다.
 */
export function StrategyListPage() {
  const navigate = useNavigate()
  const strategies = useStrategies()
  const history = useBacktestHistory()

  return (
    <section className="view active" id="view-strategies">
      <div className="page-head">
        <div>
          <h1>내 전략</h1>
          <p className="sub">저장할 때마다 새 버전이 만들어집니다. 각 전략의 현재 상태와 다음 할 일만 표시합니다.</p>
        </div>
        <Link className="btn primary" to={routes.home}>
          새 전략 만들기
        </Link>
      </div>

      {strategies.isPending && <LoadingState label="전략을 불러오는 중…" />}
      {strategies.isError && (
        <ErrorState title="전략 목록을 불러오지 못했습니다" error={strategies.error} onRetry={() => strategies.refetch()} />
      )}
      {strategies.isSuccess && strategies.data.length === 0 && (
        <EmptyState
          title="아직 저장된 전략이 없습니다"
          description="원하는 목표를 한 줄로 적으면 후보 조건·순위 기준·종목 수까지 채운 초안이 만들어집니다. 지표 이름이나 수치를 미리 알고 있어야 하는 것은 아닙니다."
          actions={
            <>
              <Link className="btn primary" to={routes.home}>
                전략 만들기
              </Link>
              <Link className="btn" to={routes.ranking}>
                공개 전략 둘러보기
              </Link>
            </>
          }
        />
      )}
      {strategies.isSuccess && strategies.data.length > 0 && (
        <div className="card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>전략</th>
                  <th>상태</th>
                  <th className="r">백테스트 수익률</th>
                  <th className="r">최대 낙폭</th>
                  <th>최근 수정</th>
                  <th style={{ textAlign: 'right' }}>다음 할 일</th>
                </tr>
              </thead>
              <tbody>
                {strategies.data.map((strategy) => (
                  <StrategyRow
                    key={strategy.id}
                    strategy={strategy}
                    runs={history.data === undefined ? null : runsForStrategy(history.data, strategy.id)}
                    onOpen={() => navigate(to.strategyDetail(strategy.id))}
                    onBacktest={() => {
                      const entry: BacktestEntryState = { strategyId: strategy.id }
                      navigate(routes.backtest, { state: entry })
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>
          {history.isError && (
            <p className="field-note crit" style={{ margin: '10px 16px 14px' }}>
              백테스트 이력을 불러오지 못해 상태와 수익률을 비워 두었습니다.
            </p>
          )}
        </div>
      )}
    </section>
  )
}

type StrategyRowProps = {
  strategy: Strategy
  /* 이력을 아직 받는 중이면 null 이다. 값을 채우지 않고 빈 칸으로 둔다. */
  runs: BacktestRun[] | null
  onOpen: () => void
  onBacktest: () => void
}

/* 이력에서 상태를 읽는다. 계산 중이 하나라도 있으면 그것이 먼저고, 완료가 있으면 검증됨, 실패만 있으면 실패다. */
function statusOf(runs: BacktestRun[], latest: BacktestRun | null): { label: string; chip: string } {
  if (runs.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING')) {
    return { label: '계산 중', chip: 'chip live' }
  }
  if (latest !== null) {
    return { label: '검증됨', chip: 'chip good' }
  }
  if (runs.some((run) => run.status === 'FAILED')) {
    return { label: '실패', chip: 'chip crit' }
  }
  return { label: '초안', chip: 'chip mute' }
}

/* 행 전체가 클릭 영역이지만 키보드 사용자는 이름 링크로 들어간다. 행 안의 버튼은 행 클릭으로 번지지 않는다. */
function StrategyRow({ strategy, runs, onOpen, onBacktest }: StrategyRowProps) {
  const latest = runs === null ? null : latestCompletedRun(runs)
  const result = useBacktestResult(latest?.id ?? null, latest !== null)
  const status = runs === null ? { label: '초안', chip: 'chip mute' } : statusOf(runs, latest)
  const metrics = result.data ?? null

  const handleRowClick = (event: MouseEvent<HTMLTableRowElement>) => {
    if ((event.target as HTMLElement).closest('a, button')) {
      return
    }
    onOpen()
  }

  return (
    <tr className="click" onClick={handleRowClick}>
      <td className="strategy-name-cell">
        <b>
          <Link to={to.strategyDetail(strategy.id)}>{strategy.name}</Link>
        </b>
        <small>{strategy.description || summarizeDsl(strategy.dsl)} · v1</small>
      </td>
      <td>
        <span className={status.chip}>{status.label}</span>
      </td>
      <td className={`r mono ${metrics === null ? 'muted-cell' : trendClass(metrics.cumulativeReturn)}`}>
        {metrics === null ? blank : formatRate(metrics.cumulativeReturn, { signed: true })}
      </td>
      <td className={`r mono ${metrics === null ? 'muted-cell' : 'dd-val'}`}>{metrics === null ? blank : formatDrawdown(metrics.mdd)}</td>
      <td className="muted-cell">{dateFormat.format(strategy.createdAt)}</td>
      <td style={{ textAlign: 'right' }}>
        {latest !== null ? (
          <Link className="rank-action" to={to.backtestRun(latest.id)}>
            결과 보기
          </Link>
        ) : (
          <button className="rank-action" type="button" onClick={onBacktest}>
            백테스트 실행
          </button>
        )}
      </td>
    </tr>
  )
}
