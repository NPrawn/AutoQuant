import { useState, type MouseEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'

import { routes, to } from '../app/routes'
import { latestCompletedRun } from '../backtest/history'
import { blank, formatDrawdown, formatRate, trendClass } from '../backtest/labels'
import { useBacktestHistory, useBacktestResult } from '../backtest/queries'
import type { BacktestRun } from '../backtest/types'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import type { BacktestEntryState } from '../strategy/backtestEntry'
import { forVersions, groupByLineage, lineageIds, versionLabel, type StrategyLineage } from '../strategy/lineage'
import { useStrategies } from '../strategy/queries'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'

/* sv-SE 로케일은 로컬 시간 기준 YYYY-MM-DD 를 준다. 목업의 날짜 표기와 같다. */
const dateFormat = new Intl.DateTimeFormat('sv-SE')

/*
 * 내 전략 목록(목업 05·23). 전략 하나는 판이 여럿이어도 한 줄이고 최신 판이 대표다.
 * 상태·수익률·낙폭은 모든 판의 백테스트를 합쳐 최근 완료 실행에서 채운다. 이력이 없으면 초안과 빈 값이다.
 * 판이 둘 이상이면 화살표로 그 밑에 옛 판 줄을 펼친다. 옛 판 줄은 그 판만의 결과를 보인다.
 * 이력 전체는 한 번 받아 행마다 거른다.
 */
export function StrategyListPage() {
  const navigate = useNavigate()
  const strategies = useStrategies()
  const history = useBacktestHistory()
  const lineages = strategies.data === undefined ? [] : groupByLineage(strategies.data)

  return (
    <section className="view active" id="view-strategies">
      <div className="page-head">
        <div>
          <h1>내 전략</h1>
          <p className="sub">
            수정해 저장하면 다음 판이 만들어지고 이전 판의 결과는 그대로 남습니다. 전략마다 최신 판 한 줄로 보이고 상태와 수익률은 모든 판의
            기록으로 판정합니다.
          </p>
        </div>
        <Link className="btn primary" to={routes.strategyNew}>
          새 전략 만들기
        </Link>
      </div>

      {strategies.isPending && <LoadingState label="전략을 불러오는 중…" />}
      {strategies.isError && (
        <ErrorState title="전략 목록을 불러오지 못했습니다" error={strategies.error} onRetry={() => strategies.refetch()} />
      )}
      {strategies.isSuccess && lineages.length === 0 && (
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
      {strategies.isSuccess && lineages.length > 0 && (
        <div className="card">
          <div className="table-scroll" tabIndex={0} role="region" aria-label="전략 목록 표">
            <table>
              <thead>
                <tr>
                  <th scope="col">전략</th>
                  <th scope="col">상태</th>
                  <th scope="col" className="r">백테스트 수익률</th>
                  <th scope="col" className="r">최대 낙폭</th>
                  <th scope="col">최근 수정</th>
                  <th scope="col" style={{ textAlign: 'right' }}>다음 할 일</th>
                </tr>
              </thead>
              <tbody>
                {lineages.map((lineage) => (
                  <StrategyRows
                    key={lineage.rootId}
                    lineage={lineage}
                    history={history.data ?? null}
                    onOpen={(strategyId) => navigate(to.strategyDetail(strategyId))}
                    onBacktest={(strategyId) => {
                      const entry: BacktestEntryState = { strategyId }
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

type StrategyRowsProps = {
  lineage: StrategyLineage
  /* 이력을 아직 받는 중이면 null 이다. 값을 채우지 않고 빈 칸으로 둔다. */
  history: BacktestRun[] | null
  onOpen: (strategyId: string) => void
  onBacktest: (strategyId: string) => void
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

/* 행 전체가 클릭 영역이지만 키보드 사용자는 이름 링크로 들어간다. 행 안의 링크·버튼은 행 클릭으로 번지지 않는다. */
function rowClick(open: () => void) {
  return (event: MouseEvent<HTMLTableRowElement>) => {
    if ((event.target as HTMLElement).closest('a, button')) {
      return
    }
    open()
  }
}

/* 대표 줄 하나와 펼쳤을 때의 옛 판 줄들. 펼침은 이 화면 안에서만 기억하고 새로고침하면 접힌다. */
function StrategyRows({ lineage, history, onOpen, onBacktest }: StrategyRowsProps) {
  const { head, versions } = lineage
  const [expanded, setExpanded] = useState(false)
  const older = versions.slice(1)
  const allRuns = history === null ? null : forVersions(history, lineageIds(lineage, head.id))

  return (
    <>
      <StrategyRow
        strategy={head}
        runs={allRuns}
        summary={
          <>
            {head.description || summarizeDsl(head.dsl)} · {versionLabel(head.version)}
            {older.length > 0 && (
              <button
                type="button"
                className="ver-toggle"
                aria-expanded={expanded}
                aria-label={`이전 판 ${older.length}개 ${expanded ? '접기' : '펼치기'}`}
                onClick={() => setExpanded((current) => !current)}
              >
                이전 판 {older.length}개
                <svg viewBox="0 0 24 24" fill="none" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M9 6l6 6-6 6" />
                </svg>
              </button>
            )}
          </>
        }
        onOpen={() => onOpen(head.id)}
        onBacktest={() => onBacktest(head.id)}
      />
      {expanded &&
        older.map((version) => (
          <StrategyRow
            key={version.id}
            strategy={version}
            runs={history === null ? null : forVersions(history, [version.id])}
            summary={version.description || summarizeDsl(version.dsl)}
            older
            onOpen={() => onOpen(version.id)}
            onBacktest={() => onBacktest(version.id)}
          />
        ))}
    </>
  )
}

type StrategyRowProps = {
  strategy: Strategy
  runs: BacktestRun[] | null
  summary: ReactNode
  /* 옛 판 줄. 들여쓰고 판 번호를 이름 앞에 둔다. */
  older?: boolean
  onOpen: () => void
  onBacktest: () => void
}

/* 줄 하나. 대표 줄은 모든 판의 이력을, 옛 판 줄은 그 판의 이력만 받는다. */
function StrategyRow({ strategy, runs, summary, older = false, onOpen, onBacktest }: StrategyRowProps) {
  const latest = runs === null ? null : latestCompletedRun(runs)
  const result = useBacktestResult(latest?.id ?? null, latest !== null)
  /* 이력을 아직 못 읽었으면 상태를 비운다. 초안이라고 먼저 적었다가 검증됨으로 바뀌면 틀린 사실을 먼저 읽게 된다. */
  const status = runs === null ? null : statusOf(runs, latest)
  const metrics = result.data ?? null

  return (
    <tr className={older ? 'click ver-row' : 'click'} onClick={rowClick(onOpen)}>
      <td className="strategy-name-cell">
        <b>
          {older && <span className="mono ver-no">{versionLabel(strategy.version)}</span>}
          <Link to={to.strategyDetail(strategy.id)}>{strategy.name}</Link>
        </b>
        <small>{summary}</small>
      </td>
      <td>
        {status === null ? <span className="muted-cell">{blank}</span> : <span className={status.chip}>{status.label}</span>}
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
        ) : older ? (
          <Link className="rank-action" to={to.strategyDetail(strategy.id)}>
            보기
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
