import { lazy, Suspense, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'

import { isApiError } from '../api/error'
import { routes } from '../app/routes'
import { blank, formatDateTime, formatKrw, formatRate, trendClass } from '../backtest/labels'
import { EquityChart } from '../components/EquityChart'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState, type ErrorDetailRow } from '../components/StateBlock'
import { computeIndicators, indicatorKeys } from '../market/indicators'
import type { CandleInterval } from '../market/model'
import { useMarketFeed } from '../market/queries'
import type { ChartAsset } from '../market/types'
import type { Timeframe } from '../strategy/types'
import { useAssetLookup, type AssetLookup } from '../simulation/assetLookup'
import { describeTransitionError } from '../simulation/errorMessage'
import { formatTime, statusOf } from '../simulation/labels'
import { totalEquity, valuePositions } from '../simulation/positions'
import { PositionsTable } from '../simulation/PositionsTable'
import {
  useLivePrices,
  useSimulationEquityCurve,
  useSimulationPositions,
  useSimulationResult,
  useSimulationRun,
  useStartSimulation,
  useStopSimulation,
} from '../simulation/queries'
import { SimulationTradeTable } from '../simulation/SimulationTradeTable'
import type { SimulationEntryState } from '../simulation/simulationEntry'
import type { SimulationResult, SimulationRun } from '../simulation/types'
import { rebalanceOf, timeframeLabel } from '../strategy/labels'
import { useStrategy } from '../strategy/queries'
import { summarizeDsl } from '../strategy/summary'
import type { Strategy } from '../strategy/types'

/*
 * 모의투자 실시간(목업 18)과 정지(목업 25). 실행 상태는 15초 폴링으로, 보유 종목의 현재가는
 * 체결 스트림으로 받는다. 랭킹 보드와 전략 활동 피드는 대응 API 가 없어 두지 않는다.
 */
const MinuteCandleChart = lazy(() => import('../market/MinuteCandleChart').then((module) => ({ default: module.MinuteCandleChart })))

/* 전략이 판단하는 봉 주기로 차트를 맞춘다. 다른 주기를 보여 주면 재선정 근거와 어긋나 보인다. */
const simulationIntervals: Record<Timeframe, CandleInterval> = { '1H': 60, '4H': 240, '1D': 1440 }

/* 보유 근거를 읽는 차트라 지표는 모두 켜 둔다. 여기서는 토글을 두지 않는다. */
const liveIndicators = new Set(indicatorKeys)

export function SimulationLivePage() {
  const { simulationId } = useParams<{ simulationId: string }>()
  const run = useSimulationRun(simulationId)
  const strategy = useStrategy(run.data?.strategyId)
  const lookup = useAssetLookup()

  if (!simulationId) {
    return <ErrorState title="모의투자를 찾을 수 없습니다" actions={<Link className="btn" to={routes.simulations}>목록으로</Link>} />
  }
  if (run.isPending) {
    return <LoadingState label="모의투자를 불러오는 중…" />
  }
  if (run.isError) {
    const notFound = isApiError(run.error) && run.error.status === 404
    return (
      <ErrorState
        title={notFound ? '모의투자를 찾을 수 없습니다' : '모의투자를 불러오지 못했습니다'}
        description={notFound ? '삭제되었거나 주소가 잘못되었습니다.' : undefined}
        error={run.error}
        onRetry={notFound ? undefined : () => run.refetch()}
        actions={
          <Link className="btn" to={routes.simulations}>
            목록으로
          </Link>
        }
      />
    )
  }

  return <LiveView run={run.data} strategy={strategy.data ?? null} lookup={lookup} />
}

type LiveViewProps = {
  run: SimulationRun
  strategy: Strategy | null
  lookup: AssetLookup
}

const noAssets: ChartAsset[] = []

function LiveView({ run, strategy, lookup }: LiveViewProps) {
  const live = run.status === 'RUNNING'
  const positions = useSimulationPositions(run.id, live)
  const result = useSimulationResult(run.id, live)
  const equity = useSimulationEquityCurve(run.id, live)
  const stop = useStopSimulation()
  const start = useStartSimulation()
  const [tradePage, setTradePage] = useState(0)
  const [transitionError, setTransitionError] = useState<string | null>(null)
  /* 차트에 띄울 보유 종목. 고르기 전에는 첫 종목을 보여 준다. */
  const [chartAssetId, setChartAssetId] = useState<string | null>(null)

  /* 운용 중일 때만 보유 종목의 체결을 구독한다. 정지된 실행은 마지막 봉 값으로 충분하다. */
  const heldAssets = useMemo(() => {
    if (!live || positions.data === undefined) return noAssets
    return positions.data.map((position) => lookup.get(position.assetId)).filter((asset): asset is ChartAsset => asset !== undefined)
  }, [live, positions.data, lookup])
  const { prices, connected } = useLivePrices(heldAssets)

  /* 체결가는 정밀도 때문에 문자열로 온다. 평가 계산에는 숫자가 필요해 여기서 바꾼다. */
  const priceMap = new Map(Array.from(prices, ([assetId, quote]) => [assetId, Number(quote.price)]))
  const valuations = valuePositions(positions.data ?? [], priceMap)
  /* 고른 종목이 재선정으로 빠졌으면 남아 있는 첫 종목으로 되돌린다. 보유 목록으로 확인해야 매도된 종목 차트가 남지 않는다. */
  const heldChartAssetId = valuations.some((valuation) => valuation.assetId === chartAssetId) ? chartAssetId : valuations[0]?.assetId
  const chartAsset = lookup.get(heldChartAssetId ?? '')
  const chartInterval = simulationIntervals[run.timeframe]
  const liveEquity = live ? totalEquity(run.cashBalance, valuations) : null
  const metrics = result.data ?? null
  const equityValue = liveEquity ?? metrics?.assetValue ?? null
  const profit = equityValue === null ? null : equityValue - run.initialCapital
  const profitRate = equityValue === null ? null : equityValue / run.initialCapital - 1

  const latestQuoteAt = Array.from(prices.values()).reduce<Date | null>((latest, quote) => (latest === null || quote.at > latest ? quote.at : latest), null)
  const updatedAt = latestQuoteAt ?? metrics?.asOf ?? run.lastCandleAt

  const transition = (action: '정지' | '재시작') => {
    const mutation = action === '정지' ? stop : start
    setTransitionError(null)
    mutation.mutate(run.id, { onError: (error) => setTransitionError(describeTransitionError(error, action)) })
  }

  const strategyName = strategy?.name ?? `전략 #${run.strategyId}`
  const status = statusOf(run.status)

  return (
    <section className="view active" id="view-simulation">
      <div className="sim-terminal">
        <div className="live-topline">
          <Link className="back" to={routes.simulations}>
            모의투자 목록
          </Link>
          <div className="live-actions">
            <span className={`stream-status ${live && (connected || heldAssets.length === 0) ? 'live' : 'paused'}`} role="status">
              <i aria-hidden="true" />
              <span>{live ? (heldAssets.length === 0 ? '운용 중 · 현금 대기' : connected ? '실시간 수신' : '시세 연결 중') : status.label}</span>
              <em className="mono">{updatedAt === null ? '--:--:--' : formatTime(updatedAt)}</em>
            </span>
            {live && (
              <button className="btn ghost sm" type="button" disabled={stop.isPending} aria-busy={stop.isPending} onClick={() => transition('정지')}>
                {stop.isPending && <span className="spin" aria-hidden="true" />}
                정지
              </button>
            )}
            {(run.status === 'STOPPED' || run.status === 'CREATED') && (
              <button className="btn primary sm" type="button" disabled={start.isPending} aria-busy={start.isPending} onClick={() => transition('재시작')}>
                {start.isPending && <span className="spin" aria-hidden="true" />}
                {run.status === 'CREATED' ? '시작' : '다시 시작'}
              </button>
            )}
          </div>
        </div>

        <div className="live-heading">
          <div>
            <h1>{strategyName}</h1>
            <p>
              {strategy !== null && `${summarizeDsl(strategy.dsl)} · `}
              {timeframeLabel(run.timeframe)} 종가 판단 · {rebalanceOf(run.rebalanceInterval).label} 재선정 · 초기 모의자산 {formatKrw(run.initialCapital)} KRW
            </p>
          </div>
        </div>

        {transitionError !== null && (
          <div className="callout crit" role="alert" style={{ marginBottom: 16 }}>
            <span>{transitionError}</span>
          </div>
        )}

        {run.status !== 'RUNNING' && <HaltedBlock run={run} metrics={metrics} pending={start.isPending} onStart={() => transition('재시작')} />}

        <div className="live-kpis">
          <div className="live-kpi">
            <span>총 평가자산</span>
            <strong className="mono">{equityValue === null ? blank : formatKrw(equityValue)}</strong>
            <small>
              {liveEquity !== null
                ? valuations.length === 0
                  ? '전량 현금'
                  : '실시간 체결가 기준'
                : metrics === null
                  ? '첫 봉 마감 전'
                  : `${formatDateTime(metrics.asOf)} 봉 기준`}
            </small>
          </div>
          <div className="live-kpi">
            <span>평가손익</span>
            <strong className={`mono ${trendClass(profit)}`}>
              {profit === null ? blank : `${profit > 0 ? '+' : ''}${formatKrw(profit)}`}
            </strong>
            <small>{formatRate(profitRate, { signed: true, digits: 2 })}</small>
          </div>
          <div className="live-kpi">
            <span>보유 종목</span>
            <strong className="mono">{positions.data === undefined ? blank : positions.data.length}</strong>
            <small>현금 {formatKrw(run.cashBalance)} KRW</small>
          </div>
          <div className="live-kpi">
            <span>마지막 재선정</span>
            <strong className="mono">{run.lastRebalanceAt === null ? '아직 없음' : formatDateTime(run.lastRebalanceAt)}</strong>
            <small>주기 {rebalanceOf(run.rebalanceInterval).label}{metrics !== null && ` · 최대 낙폭 -${(Math.abs(metrics.mdd) * 100).toFixed(1)}%`}</small>
          </div>
        </div>

        <div className="sim-live-stack">
          <section className="card pad">
            <div className="live-section-head">
              <div>
                <h3>보유 종목</h3>
              </div>
              <span className="live-note">
                {live ? (connected ? '현재가는 실시간 체결가입니다' : '시세가 연결되면 현재가와 평가액이 채워집니다') : '정지 시점의 보유 수량입니다'}
              </span>
            </div>
            {positions.isPending ? (
              <LoadingState label="보유 종목을 불러오는 중…" />
            ) : positions.isError ? (
              <ErrorState title="보유 종목을 불러오지 못했습니다" error={positions.error} onRetry={() => positions.refetch()} />
            ) : (
              <PositionsTable valuations={valuations} lookup={lookup} selectedId={chartAsset?.id ?? null} onSelect={setChartAssetId} />
            )}
          </section>

          {chartAsset !== undefined && (
            <section className="card pad">
              <div className="live-section-head">
                <div>
                  <h3>{chartAsset.marketCode} 차트</h3>
                </div>
                <span className="live-note">행을 누르면 그 종목 차트로 전환됩니다</span>
              </div>
              <HoldingChart key={chartAsset.id + ":" + chartInterval} asset={chartAsset} interval={chartInterval} />
            </section>
          )}

          <section className="card pad">
            <div className="live-section-head">
              <div>
                <h3>운용 자산 곡선</h3>
              </div>
              <span className="live-note">봉이 마감될 때마다 한 점씩 늘어납니다</span>
            </div>
            {equity.isPending ? (
              <LoadingState label="자산 곡선을 불러오는 중…" />
            ) : equity.isError ? (
              <ErrorState title="자산 곡선을 불러오지 못했습니다" error={equity.error} onRetry={() => equity.refetch()} />
            ) : equity.data.length === 0 ? (
              <p className="goal-note" style={{ margin: 0 }}>아직 마감된 봉이 없어 곡선을 그릴 수 없습니다.</p>
            ) : (
              <EquityChart points={equity.data} label="운용 기간의 자산 곡선" />
            )}
          </section>

          <section className="card pad">
            <div className="live-section-head">
              <div>
                <h3>체결 원장</h3>
              </div>
              <span className="live-note">재선정 시점의 매수·매도가 기록됩니다</span>
            </div>
            <SimulationTradeTable simulationId={run.id} live={live} lookup={lookup} page={tradePage} onPageChange={setTradePage} />
          </section>
        </div>
      </div>
    </section>
  )
}

type HaltedBlockProps = {
  run: SimulationRun
  metrics: SimulationResult | null
  pending: boolean
  onStart: () => void
}

/* 목업 25 의 상태 블록. 정지는 사용자의 선택이라 안내로, 실패는 오류로 보인다. */
function HaltedBlock({ run, metrics, pending, onStart }: HaltedBlockProps) {
  const rows: ErrorDetailRow[] = [
    { label: run.status === 'FAILED' ? '중단 시각' : '정지 시각', value: run.stoppedAt === null ? blank : formatDateTime(run.stoppedAt) },
    { label: '마지막 봉', value: run.lastCandleAt === null ? '받은 봉 없음' : formatDateTime(run.lastCandleAt) },
    { label: '현금 잔고', value: `${formatKrw(run.cashBalance)} KRW` },
    { label: '마지막 평가자산', value: metrics === null ? blank : `${formatKrw(metrics.assetValue)} KRW` },
  ]
  const backLink = (
    <Link className="btn" to={routes.simulations}>
      목록으로
    </Link>
  )
  const entry: SimulationEntryState = { strategyId: run.strategyId }

  if (run.status === 'FAILED') {
    return (
      <div style={{ marginBottom: 16 }}>
        <ErrorState
          title="운용이 중단되었습니다"
          description={run.failureReason ?? '실행 중 오류가 발생해 신호 수신을 멈췄습니다.'}
          details={rows}
          actions={
            <>
              <Link className="btn primary" to={routes.simulations} state={entry}>
                같은 전략으로 새로 시작
              </Link>
              {backLink}
            </>
          }
        />
      </div>
    )
  }

  return (
    <div style={{ marginBottom: 16 }}>
      <EmptyState
        title={run.status === 'CREATED' ? '아직 시작하지 않았습니다' : '모의투자가 정지되었습니다'}
        description={
          run.status === 'CREATED'
            ? '시작을 누르면 지금부터 실시간 봉을 받아 운용합니다.'
            : '신호 수신을 중단했습니다. 보유 수량과 체결 기록은 그대로 남아 있고, 다시 시작하면 지금 시점부터 이어서 운용합니다.'
        }
        detail={
          run.status === 'CREATED' ? undefined : (
            <div className="state-detail">
              {rows.map((row) => (
                <div key={row.label}>
                  <span>{row.label}</span>
                  <strong>{row.value}</strong>
                </div>
              ))}
            </div>
          )
        }
        actions={
          <>
            <button className="btn primary" type="button" disabled={pending} onClick={onStart}>
              {pending ? '시작 중…' : run.status === 'CREATED' ? '시작' : '다시 시작'}
            </button>
            {backLink}
          </>
        }
      />
    </div>
  )
}

/*
 * 보유 종목의 봉. 차트 화면과 같은 구독과 병합을 쓰고 지표만 고정으로 켠다.
 * 종목이나 주기가 바뀌면 키로 다시 만들어 이전 구독과 버퍼를 남기지 않는다.
 */
function HoldingChart({ asset, interval }: { asset: ChartAsset; interval: CandleInterval }) {
  const { history, candles } = useMarketFeed(asset, interval)
  const data = useMemo(() => computeIndicators(candles), [candles])

  if (history.isPending) {
    return <LoadingState label="봉을 불러오는 중…" />
  }
  if (history.isError) {
    return <ErrorState title="봉을 불러오지 못했습니다" error={history.error} onRetry={() => void history.refetch()} />
  }
  if (candles.length === 0) {
    return <p className="goal-note" style={{ margin: 0 }}>아직 이 종목의 봉이 없습니다.</p>
  }
  return (
    <Suspense fallback={<LoadingState label="차트를 준비하는 중…" />}>
      <MinuteCandleChart candles={candles} data={data} indicators={liveIndicators} />
    </Suspense>
  )
}
