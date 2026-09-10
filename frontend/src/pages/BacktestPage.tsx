import { useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'

import { routes } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { BacktestResult } from '../backtest/BacktestResult'
import { BacktestRunForm } from '../backtest/BacktestRunForm'
import { formatKrw, statusMeta } from '../backtest/labels'
import { useBacktestRun, useBacktestRuns, useCreateBacktestRun } from '../backtest/queries'
import { describeFailedRun, describeRunError } from '../backtest/runErrorMessage'
import { createRunForm, setStrategyId, toCreateRequest, validateRunForm } from '../backtest/runForm'
import { RunHistoryTable } from '../backtest/RunHistoryTable'
import { StrategyPicker } from '../backtest/StrategyPicker'
import { readBacktestEntry } from '../strategy/backtestEntry'
import { useStrategies } from '../strategy/queries'
import type { Strategy } from '../strategy/types'

/* 대기가 이 시간을 넘기면 워커가 집어가지 못하고 있다는 뜻이라 사용자에게 알린다. */
const longQueueWarningMs = 30_000

/*
 * 백테스트(목업 10, 11, 12, 24). 실행 식별자를 주소의 run 파라미터에 두어 화면을 떠났다 돌아와도
 * 진행 중인 계산을 이어서 본다. 상태 폴링은 queries 가 맡고 이 화면은 상태별 표시만 고른다.
 */
export function BacktestPage() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const runId = searchParams.get('run')
  const entry = readBacktestEntry(location.state)

  const strategies = useStrategies()
  const [today] = useState(() => new Date())
  const [form, setForm] = useState(() => createRunForm(entry?.strategyId ?? '', today))
  const [showIssues, setShowIssues] = useState(false)
  const [tradePage, setTradePage] = useState(0)

  const create = useCreateBacktestRun()
  const run = useBacktestRun(runId)

  /* 전략 목록이 도착하면 선택이 비어 있을 때만 첫 전략을 채운다. */
  if (form.strategyId === '' && strategies.data !== undefined && strategies.data.length > 0) {
    setForm(setStrategyId(form, strategies.data[0].id))
  }

  const selectedStrategy = strategies.data?.find((strategy) => strategy.id === form.strategyId) ?? null

  const startRun = () => {
    setShowIssues(true)
    if (validateRunForm(form, today).length > 0) {
      return
    }
    setTradePage(0)
    create.mutate(toCreateRequest(form), {
      onSuccess: (created) => setSearchParams({ run: created.backtestId }),
    })
  }

  const clearRun = () => {
    create.reset()
    setSearchParams({})
  }

  return (
    <section className="view active" id="view-backtest">
      <div className="page-head">
        <div>
          <span className="eyebrow">검증</span>
          <h1>백테스트</h1>
          <p className="sub">
            평가 기간 동안 이 전략이 무엇을 언제 담고 있었는지 재현하고, 전체 성과와 낙폭을 같은 축에서 분석합니다.
          </p>
        </div>
      </div>

      {strategies.isPending && <LoadingState label="전략을 불러오는 중…" />}
      {strategies.isError && (
        <ErrorState title="전략을 불러오지 못했습니다" error={strategies.error} onRetry={() => strategies.refetch()} />
      )}
      {strategies.isSuccess && strategies.data.length === 0 && (
        <EmptyState
          title="백테스트할 전략이 없습니다"
          description="전략을 먼저 만들면 그 전략으로 과거 구간을 검증할 수 있습니다."
          actions={
            <Link className="btn primary" to={routes.home}>
              전략 만들기
            </Link>
          }
        />
      )}

      {strategies.isSuccess && strategies.data.length > 0 && (
        <>
          <StrategyPicker
            strategies={strategies.data}
            selectedId={form.strategyId}
            disabled={create.isPending || runId !== null}
            onChange={(strategyId) => setForm(setStrategyId(form, strategyId))}
          />

          <div className="bt-shell">
            <BacktestRunForm
              form={form}
              strategy={selectedStrategy}
              today={today}
              pending={create.isPending || runId !== null}
              showIssues={showIssues}
              onChange={setForm}
              onSubmit={startRun}
            />

            <div>
              <RunPanel
                runId={runId}
                run={run}
                strategyName={selectedStrategy?.name ?? '선택한 전략'}
                createError={create.isError ? create.error : null}
                tradePage={tradePage}
                onTradePageChange={setTradePage}
                onClear={clearRun}
              />
            </div>
          </div>

          {runId === null && !create.isError && (
            <RecentRuns strategies={strategies.data} onOpen={(backtestId) => setSearchParams({ run: backtestId })} />
          )}
        </>
      )}
    </section>
  )
}

type RecentRunsProps = {
  strategies: Strategy[]
  onOpen: (backtestId: string) => void
}

/*
 * 실행 전 상태 아래의 최근 실행 이력. 주소의 run 파라미터를 잃어도 여기서 결과로 돌아간다.
 * 이력이 없으면 안내 패널만 남기고 표를 그리지 않는다.
 */
function RecentRuns({ strategies, onOpen }: RecentRunsProps) {
  const [page, setPage] = useState(0)
  const runs = useBacktestRuns(page)
  const strategyNames = new Map(strategies.map((strategy) => [strategy.id, strategy.name]))

  if (runs.isPending) {
    return <LoadingState label="실행 이력을 불러오는 중…" />
  }
  if (runs.isError) {
    return <ErrorState title="실행 이력을 불러오지 못했습니다" error={runs.error} onRetry={() => runs.refetch()} />
  }
  if (runs.data.totalElements === 0) {
    return null
  }

  return (
    <div style={{ marginTop: 24 }}>
      <div className="sec-head">
        <div>
          <div className="sec-title">최근 실행</div>
          <div className="sec-sub">전체 {formatKrw(runs.data.totalElements)}건 · 행을 누르면 그 결과를 다시 봅니다</div>
        </div>
      </div>
      <RunHistoryTable runs={runs.data.content} strategyNames={strategyNames} onOpen={onOpen} />
      <Pager page={page} totalPages={runs.data.totalPages} disabled={runs.isPlaceholderData} onPageChange={setPage} />
    </div>
  )
}

type RunPanelProps = {
  runId: string | null
  run: ReturnType<typeof useBacktestRun>
  strategyName: string
  createError: unknown
  tradePage: number
  onTradePageChange: (page: number) => void
  onClear: () => void
}

/* 실행 전, 접수 실패, 계산 중, 완료, 실패 다섯 상태를 고른다. */
function RunPanel({ runId, run, strategyName, createError, tradePage, onTradePageChange, onClear }: RunPanelProps) {
  if (createError !== null) {
    const view = describeRunError(createError)
    return <ErrorState title={view.title} description={view.description} details={view.details} error={createError} />
  }

  if (runId === null) {
    return (
      <div className="bt-idle">
        <div>
          왼쪽에서 조건을 설정하고 <b>백테스트 실행</b>을 누르면
          <br />
          평가 기간의 자산 곡선과 체결 내역을 보여 줍니다.
        </div>
      </div>
    )
  }

  if (run.isPending) {
    return <LoadingState label="실행 상태를 확인하는 중…" />
  }
  if (run.isError) {
    return (
      <ErrorState
        title="실행 상태를 확인하지 못했습니다"
        error={run.error}
        onRetry={() => run.refetch()}
        actions={
          <button className="btn" type="button" onClick={onClear}>
            조건 다시 설정
          </button>
        }
      />
    )
  }

  const { status, queuedAt } = run.data

  if (status === 'FAILED') {
    const view = describeFailedRun()
    return (
      <ErrorState
        title={view.title}
        description={view.description}
        actions={
          <button className="btn primary" type="button" onClick={onClear}>
            조건 다시 설정
          </button>
        }
      />
    )
  }

  if (status === 'COMPLETED') {
    return (
      <BacktestResult run={run.data} strategyName={strategyName} tradePage={tradePage} onTradePageChange={onTradePageChange} />
    )
  }

  /* 마지막 폴링 시각으로 대기 시간을 재면 렌더링이 순수하게 남고 2초마다 저절로 갱신된다. */
  const waitedTooLong = status === 'QUEUED' && run.dataUpdatedAt - queuedAt.getTime() > longQueueWarningMs

  return (
    <div className="bt-load on">
      <div className="spin" aria-hidden="true" />
      <div role="status">{statusMeta[status].label}입니다. 계산이 끝나면 결과가 표시됩니다.</div>
      {waitedTooLong && (
        <p className="goal-note" style={{ margin: 0, textAlign: 'center' }}>
          대기가 길어지고 있습니다. 계산 워커가 아직 이 작업을 가져가지 못했을 수 있습니다.
        </p>
      )}
      <button className="btn ghost sm" type="button" onClick={onClear}>
        조건 다시 설정
      </button>
    </div>
  )
}
