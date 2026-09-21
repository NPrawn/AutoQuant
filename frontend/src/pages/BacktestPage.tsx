import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'

import { routes } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { BacktestResult } from '../backtest/BacktestResult'
import { BacktestRunForm } from '../backtest/BacktestRunForm'
import { formatKrw, isRunInProgress, statusOf } from '../backtest/labels'
import { periodBounds } from '../backtest/periodBounds'
import { useBacktestRun, useBacktestRuns, useCandleCoverage, useCreateBacktestRun } from '../backtest/queries'
import { describeFailedRun, describeRunError } from '../backtest/runErrorMessage'
import {
  applyPreset,
  clampPeriodStart,
  createRunForm,
  setInitialCapital,
  setPeriodEnd,
  setPeriodStart,
  setStrategyId,
  toCreateRequest,
  validateRunForm,
} from '../backtest/runForm'
import { RunHistoryTable } from '../backtest/RunHistoryTable'
import { StrategyPicker } from '../backtest/StrategyPicker'
import { hasPeriod, readBacktestEntry } from '../strategy/backtestEntry'
import { pickerLabel, pickerOptions } from '../strategy/lineage'
import { useStrategies } from '../strategy/queries'

/* 결과 줄의 전략 표기. 지워진 전략으로 돌린 결과는 그 사실을 적는다. */
function strategyLabel(strategies: readonly Strategy[], strategyId: string): string {
  const found = strategies.find((strategy) => strategy.id === strategyId)
  return found === undefined ? '삭제된 전략' : pickerLabel(found)
}
import type { Strategy } from '../strategy/types'

/* 대기가 이 시간을 넘기면 워커가 집어가지 못하고 있다는 뜻이라 사용자에게 알린다. */
const longQueueWarningMs = 30_000

/* 오늘 날짜. 화면을 열어 둔 채 자정을 넘기면 종료일 검증과 프리셋이 어제 기준으로 남아 다음 날 첫 순간에 갱신한다. */
function useToday(): Date {
  const [today, setToday] = useState(() => new Date())
  useEffect(() => {
    const midnight = new Date(today)
    midnight.setHours(24, 0, 0, 0)
    const timer = setTimeout(() => setToday(new Date()), Math.max(1000, midnight.getTime() - Date.now() + 1000))
    return () => clearTimeout(timer)
  }, [today])
  return today
}

/* 보유 구간과 전략을 아직 모를 때의 경계. 첫 렌더의 초안 기간에만 쓰인다. */
const unknownBounds = (today: Date) => periodBounds(null, null, today)

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
  const coverage = useCandleCoverage()
  const today = useToday()
  /* "같은 조건 다시" 로 들어오면 그 기간과 초기자산으로 시작한다. 아니면 기본 폼이다. */
  const [form, setForm] = useState(() => {
    let initial = createRunForm(entry?.strategyId ?? '', today, unknownBounds(today))
    if (hasPeriod(entry)) {
      initial = setPeriodEnd(setPeriodStart(initial, entry.periodStart), entry.periodEnd)
    }
    if (entry?.initialCapital !== undefined) {
      initial = setInitialCapital(initial, entry.initialCapital)
    }
    return initial
  })
  /* 하한을 이미 반영한 전략. 전략이 바뀐 순간에만 시작일을 끌어올리려고 기억한다. */
  const [clampedFor, setClampedFor] = useState<string | null>(null)
  const [showIssues, setShowIssues] = useState(false)
  const [tradePage, setTradePage] = useState(0)

  const create = useCreateBacktestRun()
  const run = useBacktestRun(runId)

  /* 셀렉트에는 최신 버전만 올리고, 상세에서 옛 버전으로 들어왔으면 그 버전도 올린다. */
  const options = pickerOptions(strategies.data ?? [], entry?.strategyId)

  /* 전략 목록이 도착하면 선택이 비어 있을 때만 첫 전략을 채운다. */
  if (form.strategyId === '' && options.length > 0) {
    setForm(setStrategyId(form, options[0].id))
  }

  const selectedStrategy = strategies.data?.find((strategy) => strategy.id === form.strategyId) ?? null
  /*
   * 조건 폼은 실행이 아직 돌고 있을 때만 잠근다. 주소에 run 이 있다는 것만으로 잠그면 결과가 뜬 뒤에도
   * 풀리지 않아 조건을 바꿔 다시 돌릴 수 없다. 상태를 아직 못 읽은 동안은 잠가 두어 깜빡이지 않게 한다.
   */
  const runLocked =
    create.isPending || (runId !== null && (run.isPending || (run.isSuccess && isRunInProgress(run.data.status))))
  /* 결과 줄의 전략 이름은 폼의 선택이 아니라 실행이 가리키는 전략이다. 폼을 바꿔도 결과 줄은 그대로다. */
  const runStrategyName =
    run.data === undefined ? '' : strategyLabel(strategies.data ?? [], run.data.strategyId)
  /* 하한은 보유 구간과 전략이 함께 정한다. 전략마다 필요한 워밍업 봉 수가 다르다. */
  const bounds = periodBounds(coverage.data ?? null, selectedStrategy?.dsl ?? null, today)

  /*
   * 하한이 정해지면 기본 기간을 거기에 맞춘다. 그 뒤 전략을 바꿔 워밍업이 늘어나면 그때 한 번만
   * 시작일을 끌어올린다. 매 렌더마다 끌어올리면 사용자가 이른 날짜를 고를 수도, 왜 안 되는지
   * 알 수도 없다. 직접 고른 날짜는 그대로 두고 검증이 이유를 말한다.
   */
  if (coverage.isSuccess && selectedStrategy !== null && clampedFor !== selectedStrategy.id) {
    const first = clampedFor === null
    setClampedFor(selectedStrategy.id)
    /* 진입 상태가 기간을 들고 왔으면 기본 프리셋으로 덮지 않고 하한만 지킨다. */
    setForm(first && !hasPeriod(entry) ? applyPreset(form, 'all', today, bounds) : clampPeriodStart(form, bounds))
  }

  const startRun = () => {
    setShowIssues(true)
    if (validateRunForm(form, today, bounds).length > 0) {
      return
    }
    setTradePage(0)
    create.mutate(toCreateRequest(form), {
      onSuccess: (created) => setSearchParams({ run: created.backtestId }),
    })
  }

  /*
   * 캔들이 모자라 멈춘 실행의 복구. 하한으로 시작일을 옮기고 같은 조건으로 다시 접수한다.
   * 하한을 아직 못 읽었거나 이미 하한이면 되돌릴 여지가 없어 버튼을 내지 않는다.
   */
  const retryFromEarliest =
    coverage.isSuccess && form.periodStart > bounds.earliestStart
      ? {
          from: bounds.earliestStart,
          run: () => {
            const next = clampPeriodStart({ ...form, periodStart: bounds.earliestStart }, bounds)
            setForm(next)
            setTradePage(0)
            create.mutate(toCreateRequest(next), {
              onSuccess: (created) => setSearchParams({ run: created.backtestId }),
            })
          },
        }
      : null

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
            평가 기간 동안의 누적 수익을 BTC 단순보유와 견주고, 체결 하나하나를 확인합니다. 결과를 보면서 조건을 바꿔 바로 다시 돌릴 수 있습니다.
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
            strategies={options}
            selectedId={form.strategyId}
            disabled={runLocked}
            onChange={(strategyId) => setForm(setStrategyId(form, strategyId))}
          />

          <div className="bt-shell">
            <BacktestRunForm
              form={form}
              strategy={selectedStrategy}
              today={today}
              bounds={bounds}
              loadingCoverage={coverage.isPending}
              pending={runLocked}
              showIssues={showIssues}
              onChange={setForm}
              onSubmit={startRun}
            />

            <div>
              <RunPanel
                runId={runId}
                run={run}
                strategyName={runStrategyName}
                createError={create.isError ? create.error : null}
                tradePage={tradePage}
                onTradePageChange={setTradePage}
                onClear={clearRun}
                onRetryFromEarliest={retryFromEarliest}
              />
            </div>
          </div>

          {!create.isError && (
            <RecentRuns
              strategies={strategies.data}
              onOpen={(backtestId) => {
                setTradePage(0)
                setSearchParams({ run: backtestId })
              }}
            />
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
 * 실행 패널 아래의 최근 실행 이력. 결과가 떠 있어도 남겨 두어 다른 실행과 견주러 갈 수 있다.
 * 이력이 없으면 안내 패널만 남기고 표를 그리지 않는다.
 */
function RecentRuns({ strategies, onOpen }: RecentRunsProps) {
  const [page, setPage] = useState(0)
  const runs = useBacktestRuns(page)
  /* 이력에는 옛 버전으로 돌린 실행도 있어 모든 버전으로 찾고 버전을 붙여 적는다. */
  const strategyNames = new Map(strategies.map((strategy) => [strategy.id, pickerLabel(strategy)]))

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
  /* 캔들 부족으로 멈춘 실행만 쓰는 복구 동작. 되돌릴 여지가 없으면 null 이다. */
  onRetryFromEarliest: { from: string; run: () => void } | null
}

/* 실행 전, 접수 실패, 계산 중, 완료, 실패 다섯 상태를 고른다. */
function RunPanel({ runId, run, strategyName, createError, tradePage, onTradePageChange, onClear, onRetryFromEarliest }: RunPanelProps) {
  if (createError !== null) {
    const view = describeRunError(createError)
    return <ErrorState title={view.title} description={view.description} details={view.details} error={createError} />
  }

  if (runId === null) {
    return (
      <div className="bt-idle">
        <div>
          위에서 조건을 설정하고 <b>백테스트 실행</b>을 누르면
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
    const view = describeFailedRun(run.data.failureCode)
    /* 캔들이 모자라 멈춘 실행은 가능한 시작일을 알고 있으므로 그 날짜로 바로 다시 돌릴 수 있게 한다. */
    const canRetryFromEarliest = run.data.failureCode === 'INSUFFICIENT_CANDLES' && onRetryFromEarliest !== null
    return (
      <ErrorState
        title={view.title}
        description={view.description}
        actions={
          <>
            {canRetryFromEarliest && (
              <button className="btn primary" type="button" onClick={onRetryFromEarliest.run}>
                {onRetryFromEarliest.from}부터 다시 실행
              </button>
            )}
            <button className={canRetryFromEarliest ? 'btn' : 'btn primary'} type="button" onClick={onClear}>
              조건 다시 설정
            </button>
          </>
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
      <div role="status">{statusOf(status).label}입니다. 계산이 끝나면 결과가 표시됩니다.</div>
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
