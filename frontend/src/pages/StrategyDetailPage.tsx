import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'

import { isApiError } from '../api/error'
import { routes, to, type StrategyDetailTab } from '../app/routes'
import { latestCompletedRun, runsForStrategy } from '../backtest/history'
import { formatDate, formatDateTime, formatKrw } from '../backtest/labels'
import { useBacktestHistory, useBacktestResult } from '../backtest/queries'
import { ResultKpis } from '../backtest/ResultKpis'
import { RunHistoryTable } from '../backtest/RunHistoryTable'
import { TradeChart } from '../backtest/TradeChart'
import type { BacktestRun } from '../backtest/types'
import { useUniverseSize } from '../market/universe'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { describeStrategy } from '../strategy/assistant/conversation'
import type { BacktestEntryState } from '../strategy/backtestEntry'
import { draftFromDsl } from '../strategy/draft'
import { describeRanking, directionLabels, factorMeta, rebalanceMeta, timeframeLabels } from '../strategy/labels'
import { useChangeStrategyVisibility, useCopyStrategy, useDeleteStrategy, useStrategy } from '../strategy/queries'
import { summarizeFilters } from '../strategy/summary'
import { describeCopyError, describeVisibilityError } from '../strategy/translateErrorMessage'
import type { Strategy, Timeframe } from '../strategy/types'

type DetailTab = 'overview' | StrategyDetailTab

/* sv-SE 로케일은 로컬 시간 기준 YYYY-MM-DD 를 준다. 목업의 날짜 표기와 같다. */
const dateFormat = new Intl.DateTimeFormat('sv-SE')


/*
 * 전략 상세(목업 07·08·09). 목업은 공개 전략을 기준으로 그렸지만 지금은 내 전략만 있으므로
 * 작성자·대회 이력 대신 수정·백테스트·삭제 동작을 둔다. 성과 탭은 이 전략의 백테스트
 * 이력에서 최근 완료 결과를 보인다. 탭은 라우트 세그먼트로 정해 새로고침과 공유에 살아남는다.
 */
export function StrategyDetailPage() {
  const { strategyId, tab } = useParams<{ strategyId: string; tab?: string }>()
  const navigate = useNavigate()
  const strategy = useStrategy(strategyId)
  const activeTab: DetailTab = tab === 'strategy' || tab === 'code' ? tab : 'overview'

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

  return <StrategyDetail strategy={strategy.data} activeTab={activeTab} onTabChange={(next) => navigate(to.strategyDetail(strategyId, next === 'overview' ? undefined : next), { replace: true })} />
}

type StrategyDetailProps = {
  strategy: Strategy
  activeTab: DetailTab
  onTabChange: (tab: DetailTab) => void
}

function StrategyDetail({ strategy, activeTab, onTabChange }: StrategyDetailProps) {
  const navigate = useNavigate()
  const remove = useDeleteStrategy()
  const visibility = useChangeStrategyVisibility()
  const copy = useCopyStrategy()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [publishOpen, setPublishOpen] = useState(false)
  const [deleteError, setDeleteError] = useState<unknown>(null)

  const confirmPublish = () => {
    visibility.mutate(
      { strategyId: strategy.id, published: true },
      { onSettled: () => setPublishOpen(false) },
    )
  }

  const confirmCopy = () => {
    copy.mutate(strategy.id, {
      onSuccess: (created) => navigate(to.strategyDetail(String(created.strategyId))),
    })
  }

  const openBacktest = () => {
    const entry: BacktestEntryState = { strategyId: strategy.id }
    navigate(routes.backtest, { state: entry })
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

  const tabs: Array<{ id: DetailTab; label: string }> = [
    { id: 'overview', label: '성과' },
    { id: 'strategy', label: '전략 구성' },
    { id: 'code', label: '전략 코드' },
  ]

  return (
    <section className="view active" id="view-detail">
      <Link className="back" to={routes.strategies}>
        내 전략으로 돌아가기
      </Link>

      <header className="detail-hero">
        <div>
          <div className="detail-byline">
            <span className="detail-avatar" aria-hidden="true">
              {strategy.name.slice(0, 1)}
            </span>
            <span>내 전략</span>
            <i aria-hidden="true" />
            <span>{strategy.published ? '공개' : '비공개'}</span>
            <i aria-hidden="true" />
            <span>v1 · {dateFormat.format(strategy.createdAt)}</span>
          </div>
          <h1>{strategy.name}</h1>
          <p>{strategy.description || describeStrategy(draftFromDsl(strategy.dsl))}</p>
        </div>
        <div className="detail-actions">
          <button
            className="btn ghost"
            type="button"
            disabled={visibility.isPending}
            aria-pressed={strategy.published}
            onClick={() => (strategy.published ? visibility.mutate({ strategyId: strategy.id, published: false }) : setPublishOpen(true))}
          >
            {strategy.published ? '공개 중지' : '공개'}
          </button>
          <button className="btn ghost" type="button" disabled={copy.isPending} onClick={confirmCopy}>
            {copy.isPending ? '복제 중…' : '복제'}
          </button>
          <button className="btn ghost" type="button" onClick={() => setConfirmOpen(true)}>
            삭제
          </button>
          <Link className="btn" to={to.strategyEdit(strategy.id)}>
            전략 수정
          </Link>
          <button className="btn primary" type="button" onClick={openBacktest}>
            백테스트 실행
          </button>
        </div>
      </header>

      {deleteError !== null && (
        <div className="callout crit" role="alert" style={{ marginBottom: 16 }}>
          <span>{isApiError(deleteError) ? deleteError.message : '전략을 삭제하지 못했습니다.'} 잠시 후 다시 시도해 주세요.</span>
        </div>
      )}

      {visibility.isError && (
        <div className="callout crit" role="alert" style={{ marginBottom: 16 }}>
          <span>{describeVisibilityError(visibility.error)}</span>
        </div>
      )}

      {copy.isError && (
        <div className="callout crit" role="alert" style={{ marginBottom: 16 }}>
          <span>{describeCopyError(copy.error)}</span>
        </div>
      )}

      <nav className="detail-tabs" aria-label="전략 상세 메뉴">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            className={item.id === activeTab ? 'on' : undefined}
            aria-current={item.id === activeTab ? 'page' : undefined}
            onClick={() => onTabChange(item.id)}
          >
            {item.label}
          </button>
        ))}
        <span className="detail-lock">저장할 때마다 새 버전이 만들어집니다</span>
      </nav>

      <div className="detail-pane on">
        {activeTab === 'overview' && <OverviewPane strategy={strategy} onBacktest={openBacktest} />}
        {activeTab === 'strategy' && <StrategyPane strategy={strategy} />}
        {activeTab === 'code' && <CodePane strategy={strategy} />}
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
        description={`"${strategy.name}"과 그 설정이 지워집니다. 되돌릴 수 없습니다.`}
        confirmLabel="삭제"
        danger
        pending={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </section>
  )
}

type OverviewPaneProps = {
  strategy: Strategy
  onBacktest: () => void
}

/*
 * 성과 탭. 목업 07 의 종합 점수와 국면별 성과는 대회 채점이 있어야 나오는 값이라 아직 없다.
 * 대신 이 전략으로 돌린 백테스트 중 가장 최근 완료 결과의 KPI 와 실행 이력을 보인다.
 */
function OverviewPane({ strategy, onBacktest }: OverviewPaneProps) {
  const navigate = useNavigate()
  const history = useBacktestHistory()

  if (history.isPending) {
    return <LoadingState label="백테스트 이력을 불러오는 중…" />
  }
  if (history.isError) {
    return (
      <ErrorState
        title="백테스트 이력을 불러오지 못했습니다"
        error={history.error}
        onRetry={() => history.refetch()}
        actions={
          <button className="btn" type="button" onClick={onBacktest}>
            백테스트 실행
          </button>
        }
      />
    )
  }

  const runs = runsForStrategy(history.data, strategy.id)
  if (runs.length === 0) {
    return (
      <EmptyState
        title="아직 백테스트 결과가 없습니다"
        description="백테스트를 실행하면 최종 평가자산, 누적 손익, 최대 낙폭, Sharpe, 체결 수와 실행 이력이 여기에 표시됩니다."
        actions={
          <button className="btn primary" type="button" onClick={onBacktest}>
            백테스트 실행
          </button>
        }
      />
    )
  }

  const latest = latestCompletedRun(runs)
  const unsettled = runs.filter((run) => run.status === 'QUEUED' || run.status === 'RUNNING').length

  return (
    <>
      {latest !== null ? (
        <LatestResult run={latest} />
      ) : (
        <p className="goal-note" style={{ margin: '0 0 16px' }}>
          완료된 백테스트가 아직 없습니다.{' '}
          {unsettled > 0 ? `${unsettled}건이 계산 중이라 끝나면 여기에 결과가 표시됩니다.` : '실패한 실행만 있어 다시 실행해 주세요.'}
        </p>
      )}

      {latest !== null && (
        <section className="card pad shared-market-card" style={{ marginBottom: 20 }}>
          <div className="sec-title" style={{ marginBottom: 2 }}>전략 체결 차트</div>
          <TradeChart backtestId={latest.id} timeframe={latest.timeframe as Timeframe} />
        </section>
      )}

      <div className="sec-head">
        <div>
          <div className="sec-title">실행 이력</div>
          <div className="sec-sub">이 전략으로 실행한 백테스트 {runs.length}건 · 행을 누르면 그 결과로 이동합니다</div>
        </div>
        <button className="btn primary" type="button" onClick={onBacktest}>
          백테스트 실행
        </button>
      </div>
      <RunHistoryTable
        runs={runs}
        strategyNames={new Map([[strategy.id, strategy.name]])}
        showStrategy={false}
        onOpen={(backtestId) => navigate(to.backtestRun(backtestId))}
      />
    </>
  )
}

/* 최근 완료 실행의 KPI. 자산 곡선은 받지 않아 최대 낙폭 날짜는 비운다. */
function LatestResult({ run }: { run: BacktestRun }) {
  const result = useBacktestResult(run.id, true)

  if (result.isPending) {
    return <LoadingState label="최근 결과를 불러오는 중…" />
  }
  if (result.isError) {
    return <ErrorState title="최근 결과를 불러오지 못했습니다" error={result.error} onRetry={() => result.refetch()} />
  }

  return (
    <div style={{ marginBottom: 20 }}>
      <div className="result-context">
        <span>최근 완료 백테스트</span>
        <b>
          {formatDate(run.periodStart)} ~ {formatDate(run.periodEnd)} · 초기자산 {formatKrw(run.initialCapital)} KRW
          {run.completedAt !== null && ` · ${formatDateTime(run.completedAt)} 완료`}
        </b>
      </div>
      <ResultKpis metrics={result.data} initialCapital={run.initialCapital} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
        <Link className="btn" to={to.backtestRun(run.id)}>
          자산 곡선과 거래 내역 보기
        </Link>
      </div>
    </div>
  )
}

/* 전략 구성 탭. DSL 을 판단 순서대로 문장으로 읽어 준다. */
function StrategyPane({ strategy }: { strategy: Strategy }) {
  const universeSize = useUniverseSize()
  const { dsl } = strategy
  const filters = summarizeFilters(dsl)
  const weight = (100 / dsl.selection.topN).toFixed(1)
  const rebalance = rebalanceMeta[dsl.rebalance.period]

  return (
    <>
      <div className="shared-strategy-intro">
        <div>
          <h2>전략이 실제로 판단하는 순서</h2>
        </div>
        <p>저장된 후보 조건·순위·선정·재선정 규칙입니다. 수정하면 이 값을 기준으로 새 전략이 만들어집니다.</p>
      </div>

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
                {factorMeta[dsl.ranking.factor].label}이 {directionLabels[dsl.ranking.direction]}인 종목이 앞에 옵니다
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
              <li>{timeframeLabels[dsl.timeframe]} 종가 기준으로 판단</li>
              <li>{rebalance.note}</li>
            </ul>
          </div>
        </section>
      </div>

      <div className="sec-head detail-version-head">
        <div>
          <div className="sec-title">버전 이력</div>
          <div className="sec-sub">수정 API 가 준비되기 전까지는 저장할 때마다 새 전략이 만들어집니다</div>
        </div>
      </div>
      <div className="card pad">
        <div className="vrow">
          <span className="vl">v1 · 초안</span>
          <span className="muted-cell">{strategy.description || '빌더에서 저장'}</span>
          <span className="vd">{dateFormat.format(strategy.createdAt)}</span>
        </div>
      </div>
    </>
  )
}

/* 전략 코드 탭. 백엔드에 저장된 DSL 그대로다. 사용자는 코드를 쓰지 않고 빌더에서 고른다. */
function CodePane({ strategy }: { strategy: Strategy }) {
  const universeSize = useUniverseSize()
  const [copied, setCopied] = useState(false)
  const copiedTimer = useRef<number | null>(null)
  useEffect(() => () => {
    if (copiedTimer.current !== null) {
      window.clearTimeout(copiedTimer.current)
    }
  }, [])

  const code = JSON.stringify({ name: strategy.name, strategyDsl: strategy.dsl }, null, 2)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      copiedTimer.current = window.setTimeout(() => setCopied(false), 1500)
    } catch {
      /* 클립보드가 막힌 환경에서는 사용자가 직접 선택해 복사한다 */
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
            {copied ? '복사됨' : '코드 복사'}
          </button>
        </div>
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
          <strong>{timeframeLabels[strategy.dsl.timeframe]}</strong>
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
