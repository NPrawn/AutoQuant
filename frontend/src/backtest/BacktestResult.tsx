import { Link } from 'react-router'

import { to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/StateBlock'
import { EquityChart } from './EquityChart'
import { blank, formatDate, formatDrawdown, formatKrw, formatRate, formatRatio, trendClass } from './labels'
import { useBacktestEquityCurve, useBacktestResult } from './queries'
import { TradeTable } from './TradeTable'
import type { BacktestRun, EquityPoint } from './types'

type BacktestResultProps = {
  run: BacktestRun
  strategyName: string
  tradePage: number
  onTradePageChange: (page: number) => void
}

/* 낙폭이 가장 깊었던 시점. 목업의 최대 낙폭 카드가 날짜를 함께 보여 준다. */
function peakDrawdownAt(points: EquityPoint[]): Date | null {
  return points.reduce<EquityPoint | null>(
    (deepest, point) => (deepest === null || point.drawdownRate > deepest.drawdownRate ? point : deepest),
    null,
  )?.valuationAt ?? null
}

/* 목업 12 의 결과. 종목별 기여도와 비용 비교 카드는 응답에 없어 거래 내역으로 대체한다. */
export function BacktestResult({ run, strategyName, tradePage, onTradePageChange }: BacktestResultProps) {
  const result = useBacktestResult(run.id, true)
  const equity = useBacktestEquityCurve(run.id, true)

  if (result.isPending) {
    return <LoadingState label="결과를 불러오는 중…" />
  }
  if (result.isError) {
    return <ErrorState title="결과를 불러오지 못했습니다" error={result.error} onRetry={() => result.refetch()} />
  }

  const metrics = result.data
  const profit = metrics.finalAssetValue - run.initialCapital
  const points = equity.data ?? []
  const drawdownAt = peakDrawdownAt(points)

  return (
    <div className="bt-res on">
      <div className="result-context">
        <span>실행 전략</span>
        <b>{strategyName}</b>
      </div>

      <div className="bt-result-kpis">
        <div className="live-kpi">
          <span>최종 평가자산</span>
          <strong className="mono">{formatKrw(metrics.finalAssetValue)}</strong>
          <small>KRW</small>
        </div>
        <div className="live-kpi">
          <span>누적 평가손익</span>
          <strong className={`mono ${trendClass(profit)}`}>
            {profit > 0 ? '+' : ''}
            {formatKrw(profit)}
          </strong>
          <small>{formatRate(metrics.cumulativeReturn, { signed: true })}</small>
        </div>
        <div className="live-kpi">
          <span>최대 낙폭</span>
          <strong className="mono dd-val">{formatDrawdown(metrics.mdd)}</strong>
          <small>{drawdownAt === null ? '기간 중 최대' : formatDate(drawdownAt)}</small>
        </div>
        <div className="live-kpi">
          <span>Sharpe</span>
          <strong className="mono">{formatRatio(metrics.sharpeRatio)}</strong>
          <small>연 변동성 {formatRate(metrics.volatility)}</small>
        </div>
        <div className="live-kpi">
          <span>전체 체결</span>
          <strong className="mono">{formatKrw(metrics.tradeCount)}</strong>
          <small>
            승률 {formatRate(metrics.winRate)} · 수수료 {formatKrw(metrics.totalFee)} KRW
          </small>
        </div>
      </div>

      <section className="card card--lead perf-stack">
        <div className="perf-head">
          <div>
            <div className="sec-title">누적 자산 곡선</div>
            <div className="sec-sub">
              {formatDate(run.periodStart)} ~ {formatDate(run.periodEnd)} · 평가 시점 {points.length}개
            </div>
          </div>
        </div>
        <div className="perf-plot">
          {equity.isPending ? (
            <LoadingState label="자산 곡선을 불러오는 중…" />
          ) : equity.isError ? (
            <ErrorState title="자산 곡선을 불러오지 못했습니다" error={equity.error} onRetry={() => equity.refetch()} />
          ) : points.length === 0 ? (
            <p className="goal-note">평가 시점 기록이 없어 곡선을 그릴 수 없습니다.</p>
          ) : (
            <EquityChart points={points} />
          )}
        </div>
        <div className="perf-foot">
          <span>
            연평균 수익률 <b>{formatRate(metrics.cagr, { signed: true })}</b>
          </span>
          <span className="mono">
            전략 {formatRate(metrics.cumulativeReturn, { signed: true })} · 벤치마크{' '}
            {metrics.benchmarkReturn === null ? blank : formatRate(metrics.benchmarkReturn, { signed: true })}
          </span>
        </div>
      </section>

      <TradeTable backtestId={run.id} page={tradePage} onPageChange={onTradePageChange} />

      <p className="goal-note" style={{ margin: '20px 0 0' }}>
        과거 성과는 미래 성과를 보장하지 않습니다. 업비트 KRW 상위 30 기준으로 언제든 재현할 수 있습니다.
      </p>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
        <Link className="btn ghost" to={to.strategyEdit(run.strategyId)}>
          전략 수정
        </Link>
        <button className="btn primary" type="button" disabled title="모의투자는 백엔드 준비 후 열립니다">
          이 전략으로 모의투자
        </button>
      </div>
    </div>
  )
}
