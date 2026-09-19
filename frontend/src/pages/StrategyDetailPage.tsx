import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { isApiError } from '../api/error'
import { routes, to, type StrategyDetailTab } from '../app/routes'
import { latestCompletedRun, runsForStrategy } from '../backtest/history'
import { formatDate, formatDateTime, formatDrawdown, formatKrw, formatRate, statusOf as backtestStatusOf, trendClass } from '../backtest/labels'
import { useBacktestHistory, useBacktestResult } from '../backtest/queries'
import type { BacktestRun } from '../backtest/types'
import { contestStatusLabels } from '../contest/labels'
import { useContests } from '../contest/queries'
import type { ContestRoundItemResponse } from '../contest/types'
import { useUniverseSize } from '../market/universe'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { statusOf as simulationStatusOf } from '../simulation/labels'
import { useSimulationHistory, useSimulationResult } from '../simulation/queries'
import type { SimulationEntryState } from '../simulation/simulationEntry'
import type { SimulationRun } from '../simulation/types'
import { describeStrategy } from '../strategy/assistant/conversation'
import type { BacktestEntryState } from '../strategy/backtestEntry'
import { draftFromDsl } from '../strategy/draft'
import { describeRanking, directionLabel, factorOf, rebalanceOf, timeframeLabel } from '../strategy/labels'
import { useChangeStrategyVisibility, useCopyStrategy, useDeleteStrategy, useStrategy } from '../strategy/queries'
import { primaryAction, runningSimulations, simulationsForStrategy, stageMeta, strategyStage } from '../strategy/stage'
import { summarizeFilters } from '../strategy/summary'
import { describeCopyError, describeVisibilityError } from '../strategy/translateErrorMessage'
import type { Strategy } from '../strategy/types'

type HistoryTab = 'backtests' | StrategyDetailTab

/* sv-SE 로케일은 로컬 시간 기준 YYYY-MM-DD 를 준다. 목업의 날짜 표기와 같다. */
const dateFormat = new Intl.DateTimeFormat('sv-SE')

/*
 * 전략 상세. 이 전략으로 한 일을 한 곳에 모아 관리한다. 위에 지금 상태 카드 셋, 그 아래 백테스트·모의투자
 * 기록, 맨 아래 접어 둔 구성·코드·공개. 목업 07 은 공개 전략 보기라 참고하지 않고 사용자의 동선을 따랐다.
 * 기록 탭은 라우트 세그먼트로 정해 새로고침과 공유에 살아남는다.
 */
export function StrategyDetailPage() {
  const { strategyId, tab } = useParams<{ strategyId: string; tab?: string }>()
  const navigate = useNavigate()
  const strategy = useStrategy(strategyId)
  const historyTab: HistoryTab = tab === 'simulations' ? 'simulations' : 'backtests'

  if (!strategyId) {
    return <ErrorState title="전략을 찾을 수 없습니다" actions={<Link className="btn" to={routes.strategies}>목록으로</Link>} />
  }
  if (strategy.isPending) {
    return <LoadingState label="전략을 불러오는 중…" />
  }
  if (strategy.isError) {
    const notFound = isApiError(strategy.error) && strategy.error.status === 404
    return (
      <ErrorState
        title={notFound ? '전략을 찾을 수 없습니다' : '전략을 불러오지 못했습니다'}
        description={notFound ? '삭제되었거나 주소가 잘못되었습니다.' : undefined}
        error={strategy.error}
        onRetry={notFound ? undefined : () => strategy.refetch()}
        actions={
          <Link className="btn" to={routes.strategies}>
            목록으로
          </Link>
        }
      />
    )
  }

  return (
    <StrategyDetail
      strategy={strategy.data}
      historyTab={historyTab}
      onTabChange={(next) => navigate(to.strategyDetail(strategyId, next === 'backtests' ? undefined : next), { replace: true })}
    />
  )
}

type StrategyDetailProps = {
  strategy: Strategy
  historyTab: HistoryTab
  onTabChange: (tab: HistoryTab) => void
}

function StrategyDetail({ strategy, historyTab, onTabChange }: StrategyDetailProps) {
  const navigate = useNavigate()
  const remove = useDeleteStrategy()
  const visibility = useChangeStrategyVisibility()
  const copy = useCopyStrategy()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [publishOpen, setPublishOpen] = useState(false)
  const [deleteError, setDeleteError] = useState<unknown>(null)

  /* 이 전략으로 한 일. 세 조회는 목록 화면과 캐시를 나눠 쓰고, 이 화면은 전략 하나로 거른다. */
  const backtests = useBacktestHistory()
  const simulations = useSimulationHistory()
  const contests = useContests()
  const runs = backtests.data === undefined ? [] : runsForStrategy(backtests.data, strategy.id)
  const sims = simulations.data === undefined ? [] : simulationsForStrategy(simulations.data, strategy.id)
  const submission = submissionFor(contests.data?.contests ?? [], strategy.id)
  const stageInput = { runs, simulations: sims, submitted: submission !== null }
  const stage = strategyStage(stageInput)
  const primary = primaryAction(stageInput)
  const running = runningSimulations(sims)
  const filters = summarizeFilters(strategy.dsl)

  const confirmPublish = () => {
    visibility.mutate({ strategyId: strategy.id, published: true }, { onSettled: () => setPublishOpen(false) })
  }

  const confirmCopy = () => {
    copy.mutate(strategy.id, {
      onSuccess: (created) => navigate(to.strategyDetail(String(created.strategyId))),
    })
  }

  /* 실행 하나를 넘기면 "같은 조건 다시" 다. 그 기간과 초기자산이 폼에 채워진다. */
  const openBacktest = (run?: BacktestRun) => {
    const entry: BacktestEntryState =
      run === undefined
        ? { strategyId: strategy.id }
        : { strategyId: strategy.id, periodStart: formatDate(run.periodStart), periodEnd: formatDate(run.periodEnd), initialCapital: run.initialCapital }
    navigate(routes.backtest, { state: entry })
  }

  const openSimulation = () => {
    const entry: SimulationEntryState = { strategyId: strategy.id }
    navigate(routes.simulations, { state: entry })
  }

  const runPrimary = () => {
    if (primary.kind === 'backtest') openBacktest()
    else if (primary.kind === 'simulate') openSimulation()
    else navigate(to.simulationLive(primary.simulationId))
  }

  const confirmDelete = () => {
    remove.mutate(strategy.id, {
      onSuccess: () => navigate(routes.strategies, { replace: true }),
      onError: (error) => {
        setConfirmOpen(false)
        setDeleteError(error)
      },
    })
  }

  return (
    <section className="view active" id="view-detail">
      <Link className="back" to={routes.strategies}>
        내 전략으로 돌아가기
      </Link>

      <header className="detail-hero">
        <div>
          <div className="detail-byline">
            <span className="detail-avatar" aria-hidden="true">
              {[...strategy.name][0] ?? '?'}
            </span>
            <span className={stageMeta[stage].chip}>{stageMeta[stage].label}</span>
            <i aria-hidden="true" />
            <span>{strategy.published ? '공개' : '비공개'}</span>
            <i aria-hidden="true" />
            <span>{dateFormat.format(strategy.createdAt)} 만듦</span>
          </div>
          <h1>{strategy.name}</h1>
          <p>{strategy.description || describeStrategy(draftFromDsl(strategy.dsl))}</p>
        </div>
        <div className="detail-actions">
          <Link className="btn ghost quiet" to={to.strategyEdit(strategy.id)}>
            수정
          </Link>
          <button className="btn ghost quiet" type="button" disabled={copy.isPending} aria-busy={copy.isPending} onClick={confirmCopy}>
            {copy.isPending && <span className="spin" aria-hidden="true" />}
            복제
          </button>
          <button className="btn ghost quiet danger" type="button" onClick={() => setConfirmOpen(true)}>
            삭제
          </button>
          <button
            className="btn ghost"
            type="button"
            disabled={visibility.isPending}
            aria-busy={visibility.isPending}
            onClick={() => (strategy.published ? visibility.mutate({ strategyId: strategy.id, published: false }) : setPublishOpen(true))}
          >
            {visibility.isPending && <span className="spin" aria-hidden="true" />}
            {strategy.published ? '공개 중지' : '공개'}
          </button>
          {/* 주 버튼은 하나고 단계에 따라 바뀐다. 초안은 백테스트, 검증됐으면 모의투자, 운용 중이면 실시간이다. */}
          <button className="btn primary" type="button" onClick={runPrimary}>
            {primary.label}
          </button>
        </div>
      </header>

      {deleteError !== null && (
        <div className="callout crit" role="alert">
          <span>{isApiError(deleteError) ? deleteError.message : '전략을 삭제하지 못했습니다.'} 잠시 후 다시 시도해 주세요.</span>
        </div>
      )}
      {visibility.isError && (
        <div className="callout crit" role="alert">
          <span>{describeVisibilityError(visibility.error)}</span>
        </div>
      )}
      {copy.isError && (
        <div className="callout crit" role="alert">
          <span>{describeCopyError(copy.error)}</span>
        </div>
      )}

      <NowCards
        runs={runs}
        sims={sims}
        submission={submission}
        loading={{ backtests: backtests.isPending, simulations: simulations.isPending, contests: contests.isPending }}
        onBacktest={() => openBacktest()}
      />

      <div className="sec-head">
        <div>
          <div className="sec-title">이 전략으로 한 일</div>
          <div className="sec-sub">행을 누르면 그 실행으로 갑니다. 같은 조건 다시는 기간과 초기자산을 채운 채 백테스트 폼을 엽니다.</div>
        </div>
      </div>
      <div className="hist-tabs" role="tablist" aria-label="기록 종류">
        <button
          type="button"
          role="tab"
          className={historyTab === 'backtests' ? 'on' : undefined}
          aria-selected={historyTab === 'backtests'}
          onClick={() => onTabChange('backtests')}
        >
          백테스트<i>{runs.length}</i>
        </button>
        <button
          type="button"
          role="tab"
          className={historyTab === 'simulations' ? 'on' : undefined}
          aria-selected={historyTab === 'simulations'}
          onClick={() => onTabChange('simulations')}
        >
          모의투자<i>{sims.length}</i>
        </button>
      </div>
      {historyTab === 'backtests' ? (
        <BacktestHistory query={backtests} runs={runs} onOpen={(id) => navigate(to.backtestRun(id))} onRerun={openBacktest} onNew={() => openBacktest()} />
      ) : (
        <SimulationHistory query={simulations} sims={sims} onOpen={(id) => navigate(to.simulationLive(id))} onNew={openSimulation} />
      )}

      <div className="detail-fold">
        <details>
          <summary>
            전략 구성 자세히 · 조건 {filters.conditions.length + filters.otherCount}개 · {describeRanking(strategy.dsl.ranking)} ·{' '}
            {rebalanceOf(strategy.dsl.rebalance.period).label} · Top {strategy.dsl.selection.topN}
          </summary>
          <div className="fold-body">
            <StrategyPane strategy={strategy} />
          </div>
        </details>
        <details>
          <summary>전략 코드 · JSON</summary>
          <div className="fold-body">
            <CodePane strategy={strategy} />
          </div>
        </details>
        <details>
          <summary>공개 설정 · {strategy.published ? '공개' : '비공개'}</summary>
          <div className="fold-body">
            <p className="goal-note" style={{ margin: 0 }}>
              공개하면 랭킹에서 다른 사람이 이 전략의 구성을 보고 복제할 수 있습니다. 공개는 확인을 한 번 묻고 비공개로 되돌리기는 바로 됩니다. 수정해
              저장하면 별도의 새 전략이 만들어집니다.
            </p>
          </div>
        </details>
      </div>

      <ConfirmDialog
        open={publishOpen}
        title="이 전략을 공개할까요?"
        description="공개하면 다른 사용자가 이 전략의 구성을 보고 복제할 수 있습니다. 언제든 다시 비공개로 바꿀 수 있습니다."
        confirmLabel="공개"
        pending={visibility.isPending}
        onConfirm={confirmPublish}
        onCancel={() => setPublishOpen(false)}
      />

      <ConfirmDialog
        open={confirmOpen}
        title="이 전략을 삭제할까요?"
        description={
          running.length > 0
            ? `"${strategy.name}"과 그 설정이 지워집니다. 운용 중인 모의투자 ${running.length}건이 이 전략을 쓰고 있습니다. 먼저 정지하는 것이 안전합니다. 되돌릴 수 없습니다.`
            : `"${strategy.name}"과 그 설정이 지워집니다. 백테스트와 모의투자 기록은 남습니다. 되돌릴 수 없습니다.`
        }
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </section>
  )
}

/* 대회 목록에서 이 전략을 낸 회차. 여럿이면 마감이 늦은 회차를 본다. */
function submissionFor(rounds: readonly ContestRoundItemResponse[], strategyId: string): ContestRoundItemResponse | null {
  const mine = rounds.filter((round) => round.mySubmission !== null && String(round.mySubmission.strategyId) === strategyId)
  mine.sort((left, right) => right.submissionEndsAt.localeCompare(left.submissionEndsAt))
  return mine[0] ?? null
}

type NowCardsProps = {
  runs: BacktestRun[]
  sims: SimulationRun[]
  submission: ContestRoundItemResponse | null
  loading: { backtests: boolean; simulations: boolean; contests: boolean }
  onBacktest: () => void
}

/* 돌아와서 3초 안에 볼 것. 지금 돌아가는 것, 지난 결과, 대회. 각 카드에서 한 번에 그 화면으로 간다. */
function NowCards({ runs, sims, submission, loading, onBacktest }: NowCardsProps) {
  const running = runningSimulations(sims)
  const latest = latestCompletedRun(runs)
  const result = useBacktestResult(latest?.id ?? null, latest !== null)

  return (
    <div className="now-grid">
      <div className="now-card">
        <span>지금</span>
        {loading.simulations ? (
          <strong className="muted">…</strong>
        ) : running.length > 0 ? (
          <>
            <strong>
              <span className="chip live">운용 중</span>
              {running.length}건
            </strong>
            <Link to={to.simulationLive(running[0].id)}>실시간 운용 보기 →</Link>
          </>
        ) : (
          <>
            <strong className="muted">운용 중인 모의투자 없음</strong>
            <span className="muted">{sims.length > 0 ? `지난 실행 ${sims.length}건은 아래 기록에 있습니다` : '검증이 끝났으면 모의투자를 시작해 보세요'}</span>
          </>
        )}
      </div>

      <div className="now-card">
        <span>{latest !== null && latest.completedAt !== null ? `최근 백테스트 · ${formatDate(latest.completedAt)}` : '최근 백테스트'}</span>
        {loading.backtests || (latest !== null && result.isPending) ? (
          <strong className="muted">…</strong>
        ) : latest === null ? (
          <>
            <strong className="muted">아직 없음</strong>
            <button className="link" type="button" onClick={onBacktest}>
              백테스트 실행 →
            </button>
          </>
        ) : result.isError ? (
          <>
            <strong className="muted">결과를 못 읽음</strong>
            <Link to={to.backtestRun(latest.id)}>결과 화면에서 보기 →</Link>
          </>
        ) : result.data === undefined ? (
          <strong className="muted">…</strong>
        ) : (
          <>
            <strong className={`mono ${trendClass(result.data.cumulativeReturn)}`}>{formatRate(result.data.cumulativeReturn, { signed: true })}</strong>
            <Link to={to.backtestRun(latest.id)}>최대 낙폭 {formatDrawdown(result.data.mdd)} · 결과 보기 →</Link>
          </>
        )}
      </div>

      <div className="now-card">
        <span>대회</span>
        {loading.contests ? (
          <strong className="muted">…</strong>
        ) : submission === null ? (
          <>
            <strong className="muted">제출 없음</strong>
            <Link to={routes.contests}>열린 회차 보기 →</Link>
          </>
        ) : (
          <>
            <strong>{submission.title}</strong>
            <Link to={to.contestResult(submission.roundId)}>
              {contestStatusLabels[submission.status] ?? submission.status}
              {submission.myRanking !== null && ` · ${submission.myRanking}위`} · 내 결과 →
            </Link>
          </>
        )}
      </div>
    </div>
  )
}

type BacktestHistoryProps = {
  query: ReturnType<typeof useBacktestHistory>
  runs: BacktestRun[]
  onOpen: (backtestId: string) => void
  onRerun: (run: BacktestRun) => void
  onNew: () => void
}

function BacktestHistory({ query, runs, onOpen, onRerun, onNew }: BacktestHistoryProps) {
  if (query.isPending) {
    return <LoadingState label="백테스트 이력을 불러오는 중…" />
  }
  if (query.isError) {
    return <ErrorState title="백테스트 이력을 불러오지 못했습니다" error={query.error} onRetry={() => query.refetch()} />
  }
  if (runs.length === 0) {
    return (
      <EmptyState
        title="아직 백테스트가 없습니다"
        description="과거 구간으로 이 전략을 검증하면 여기에 실행마다 한 줄씩 쌓입니다."
        actions={
          <button className="btn primary" type="button" onClick={onNew}>
            백테스트 실행
          </button>
        }
      />
    )
  }

  return (
    <div className="card">
      <div className="table-scroll" tabIndex={0} role="region" aria-label="백테스트 기록 표">
        <table>
          <thead>
            <tr>
              <th scope="col">상태</th>
              <th scope="col">평가 기간</th>
              <th scope="col" className="r">
                초기자산
              </th>
              <th scope="col" className="r">
                수익률
              </th>
              <th scope="col" className="r">
                최대 낙폭
              </th>
              <th scope="col">접수</th>
              <th scope="col" className="r">
                동작
              </th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => (
              <BacktestRow key={run.id} run={run} onOpen={() => onOpen(run.id)} onRerun={() => onRerun(run)} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* 행 전체가 클릭 영역이지만 안의 버튼은 행 클릭으로 번지지 않는다. */
function rowClick(open: () => void) {
  return (event: MouseEvent<HTMLTableRowElement>) => {
    if ((event.target as HTMLElement).closest('a, button')) {
      return
    }
    open()
  }
}

/* 완료된 실행만 결과를 읽는다. 행 수가 전략 하나의 실행 수라 크지 않다. */
function BacktestRow({ run, onOpen, onRerun }: { run: BacktestRun; onOpen: () => void; onRerun: () => void }) {
  const completed = run.status === 'COMPLETED'
  const result = useBacktestResult(run.id, completed)
  const status = backtestStatusOf(run.status)
  const metrics = completed ? (result.data ?? null) : null

  return (
    <tr className="click" onClick={rowClick(onOpen)}>
      <td>
        <span className={status.chip}>{status.label}</span>
      </td>
      <td className="mono muted-cell">
        {formatDate(run.periodStart)} ~ {formatDate(run.periodEnd)}
      </td>
      <td className="r mono">{formatKrw(run.initialCapital)}</td>
      <td className={`r mono ${metrics === null ? 'muted-cell' : trendClass(metrics.cumulativeReturn)}`}>
        {metrics === null ? '—' : formatRate(metrics.cumulativeReturn, { signed: true })}
      </td>
      <td className={`r mono ${metrics === null ? 'muted-cell' : 'dd-val'}`}>{metrics === null ? '—' : formatDrawdown(metrics.mdd)}</td>
      <td className="muted-cell">{formatDateTime(run.queuedAt)}</td>
      <td>
        <div className="row-acts">
          <button className="rank-action" type="button" onClick={onOpen}>
            {run.status === 'COMPLETED' ? '결과 보기' : run.status === 'FAILED' ? '사유' : '진행 보기'}
          </button>
          <button className="rank-action" type="button" onClick={onRerun}>
            {run.status === 'FAILED' ? '다시 실행' : '같은 조건 다시'}
          </button>
        </div>
      </td>
    </tr>
  )
}

type SimulationHistoryProps = {
  query: ReturnType<typeof useSimulationHistory>
  sims: SimulationRun[]
  onOpen: (simulationId: string) => void
  onNew: () => void
}

function SimulationHistory({ query, sims, onOpen, onNew }: SimulationHistoryProps) {
  if (query.isPending) {
    return <LoadingState label="모의투자 이력을 불러오는 중…" />
  }
  if (query.isError) {
    return <ErrorState title="모의투자 이력을 불러오지 못했습니다" error={query.error} onRetry={() => query.refetch()} />
  }
  if (sims.length === 0) {
    return (
      <EmptyState
        title="아직 모의투자가 없습니다"
        description="실제 시세로 이 전략을 운용해 보면 여기에 실행마다 한 줄씩 쌓입니다."
        actions={
          <button className="btn primary" type="button" onClick={onNew}>
            모의투자 시작
          </button>
        }
      />
    )
  }

  return (
    <div className="card">
      <div className="table-scroll" tabIndex={0} role="region" aria-label="모의투자 기록 표">
        <table>
          <thead>
            <tr>
              <th scope="col">상태</th>
              <th scope="col">운용 기간</th>
              <th scope="col" className="r">
                초기자산
              </th>
              <th scope="col" className="r">
                평가자산
              </th>
              <th scope="col" className="r">
                수익률
              </th>
              <th scope="col" className="r">
                동작
              </th>
            </tr>
          </thead>
          <tbody>
            {sims.map((run) => (
              <SimulationRow key={run.id} run={run} onOpen={() => onOpen(run.id)} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* 결과 행은 첫 봉이 마감되기 전에는 없다. 그 동안은 비운다. 목록 카드와 같이 폴링은 하지 않는다. */
function SimulationRow({ run, onOpen }: { run: SimulationRun; onOpen: () => void }) {
  const result = useSimulationResult(run.id, false)
  const status = simulationStatusOf(run.status)
  const metrics = result.data ?? null

  return (
    <tr className="click" onClick={rowClick(onOpen)}>
      <td>
        <span className={status.chip}>{status.label}</span>
      </td>
      <td className="mono muted-cell">
        {run.startedAt === null ? '시작 전' : `${formatDate(run.startedAt)} ~ ${run.stoppedAt === null ? '' : formatDate(run.stoppedAt)}`}
      </td>
      <td className="r mono">{formatKrw(run.initialCapital)}</td>
      <td className="r mono">{metrics === null ? '—' : formatKrw(metrics.assetValue)}</td>
      <td className={`r mono ${metrics === null ? 'muted-cell' : trendClass(metrics.cumulativeReturn)}`}>
        {metrics === null ? '—' : formatRate(metrics.cumulativeReturn, { signed: true })}
      </td>
      <td>
        <div className="row-acts">
          <button className="rank-action" type="button" onClick={onOpen}>
            {run.status === 'RUNNING' ? '실시간 보기' : '기록 보기'}
          </button>
        </div>
      </td>
    </tr>
  )
}

/* 전략 구성. DSL 을 판단 순서대로 문장으로 읽어 준다. 접힌 상태로 아래에 둔다. */
function StrategyPane({ strategy }: { strategy: Strategy }) {
  const universeSize = useUniverseSize()
  const { dsl } = strategy
  const filters = summarizeFilters(dsl)
  const weight = (100 / dsl.selection.topN).toFixed(1)
  const rebalance = rebalanceOf(dsl.rebalance.period)

  return (
    <div className="shared-rule-grid">
      <section className="shared-rule signal">
        <span>01</span>
        <div>
          <h3>Filter · 후보 선별</h3>
          <ul>
            <li>
              후보군은 업비트 KRW 거래대금 <b>상위 {universeSize}종목</b> (고정)
            </li>
            {filters.conditions.map((condition, index) => (
              <li key={condition}>
                {index > 0 && `${filters.logic} · `}
                <b>{condition}</b>
              </li>
            ))}
            {filters.otherCount > 0 && <li>화면에서 편집할 수 없는 조건 {filters.otherCount}개가 함께 적용됩니다</li>}
            {filters.conditions.length === 0 && filters.otherCount === 0 && <li>후보 조건 없음</li>}
          </ul>
        </div>
      </section>

      <section className="shared-rule allocation">
        <span>02</span>
        <div>
          <h3>Ranking · 순위</h3>
          <ul>
            <li>
              <b>{describeRanking(dsl.ranking)}</b> 으로 통과 종목을 정렬
            </li>
            <li>
              {factorOf(dsl.ranking.factor).label}이 {directionLabel(dsl.ranking.direction)}인 종목이 앞에 옵니다
            </li>
            <li>종목 간 비교가 가능하도록 절대가격이 아닌 비율 팩터만 사용</li>
          </ul>
        </div>
      </section>

      <section className="shared-rule risk">
        <span>03</span>
        <div>
          <h3>Selection · 투자 종목</h3>
          <ul>
            <li>
              상위 <b>Top {dsl.selection.topN}</b>를 실제 투자 대상으로 선정
            </li>
            <li>
              <b>동일 비중</b> — 각 {weight}% (고정 정책)
            </li>
            <li>
              매도 조건은 <b>Top {dsl.selection.topN} 탈락</b> 하나뿐
            </li>
          </ul>
        </div>
      </section>

      <section className="shared-rule execution">
        <span>04</span>
        <div>
          <h3>Rebalancing · 재선정</h3>
          <ul>
            <li>
              <b>{rebalance.label}</b> Filter와 Ranking을 다시 수행
            </li>
            <li>{timeframeLabel(dsl.timeframe)} 종가 기준으로 판단</li>
            <li>{rebalance.note}</li>
          </ul>
        </div>
      </section>
    </div>
  )
}

/* 전략 코드. 백엔드에 저장된 DSL 그대로다. 사용자는 코드를 쓰지 않고 빌더에서 고른다. */
function CodePane({ strategy }: { strategy: Strategy }) {
  const universeSize = useUniverseSize()
  const [copied, setCopied] = useState(false)
  const [copyFailed, setCopyFailed] = useState(false)
  const copiedTimer = useRef<number | null>(null)
  useEffect(
    () => () => {
      if (copiedTimer.current !== null) {
        window.clearTimeout(copiedTimer.current)
      }
    },
    [],
  )

  const code = JSON.stringify({ name: strategy.name, strategyDsl: strategy.dsl }, null, 2)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopyFailed(false)
      setCopied(true)
      copiedTimer.current = window.setTimeout(() => setCopied(false), 1500)
    } catch {
      /* 클립보드가 막힌 환경. 직접 선택해 복사하라고 알린다. */
      setCopyFailed(true)
    }
  }

  return (
    <div className="shared-code-layout">
      <section className="card shared-code-card">
        <div className="shared-code-head">
          <div>
            <h3>전략 코드</h3>
          </div>
          <button className="btn ghost sm" type="button" onClick={copy}>
            코드 복사
          </button>
        </div>
        <p className="sr-only" role="status">
          {copied ? '코드를 복사했습니다' : ''}
        </p>
        {copied && <p className="field-note">코드를 복사했습니다.</p>}
        {copyFailed && <p className="field-note crit">이 환경에서는 복사할 수 없습니다. 코드를 직접 선택해 복사해 주세요.</p>}
        <pre>
          {code.split('\n').map((line, index) => (
            <code key={index}>{line}</code>
          ))}
        </pre>
      </section>
      <aside className="card shared-code-info">
        <h3>실행 환경</h3>
        <div>
          <span>거래소</span>
          <strong>Upbit</strong>
        </div>
        <div>
          <span>기준 통화</span>
          <strong>KRW</strong>
        </div>
        <div>
          <span>후보군</span>
          <strong>거래대금 상위 {universeSize}</strong>
        </div>
        <div>
          <span>데이터 주기</span>
          <strong>{timeframeLabel(strategy.dsl.timeframe)}</strong>
        </div>
        <div>
          <span>자산 배분</span>
          <strong>동일 비중</strong>
        </div>
        <p>같은 DSL을 백테스트와 모의투자가 함께 씁니다. 사용자는 코드를 직접 쓰지 않고 편집 화면에서 조건을 고르면 이 JSON으로 저장됩니다.</p>
      </aside>
    </div>
  )
}
