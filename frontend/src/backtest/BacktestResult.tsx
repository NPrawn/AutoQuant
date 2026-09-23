import { Link } from 'react-router'

import { routes, to } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/StateBlock'
import type { SimulationEntryState } from '../simulation/simulationEntry'
import type { StrategyLabel } from '../strategy/lineage'
import { StrategyName } from '../strategy/StrategyName'
import { blank, formatDate, formatKrw, formatRate } from './labels'
import { formatIndex, indexBenchmark, indexEquity, rebalanceCycles } from './performance'
import { PerformanceChart } from './PerformanceChart'
import {
  useBacktestBenchmark,
  useBacktestEquityCurve,
  useBacktestResult,
  useBacktestTradeMarkers,
  useCandleCoverage,
} from './queries'
import { ResultKpis } from './ResultKpis'
import { TradeTable } from './TradeTable'
import type { BacktestRun, EquityPoint } from './types'

type BacktestResultProps = {
  run: BacktestRun
  strategyName: StrategyLabel
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
  const trades = useBacktestTradeMarkers(run.id, true)
  const benchmarkCandles = useBacktestBenchmark(true)
  const coverage = useCandleCoverage()

  if (result.isPending) {
    return <LoadingState label="결과를 불러오는 중…" />
  }
  if (result.isError) {
    return <ErrorState title="결과를 불러오지 못했습니다" error={result.error} onRetry={() => result.refetch()} />
  }

  const metrics = result.data
  const points = equity.data ?? []
  /* 차트가 쓰는 값은 전부 여기서 만든다. 체결이나 벤치마크가 아직 안 왔으면 그 부분만 비운 채 그린다. */
  const indexed = indexEquity(points)
  const benchmark = indexBenchmark(benchmarkCandles.data ?? [], points)
  const cycles = rebalanceCycles(trades.data ?? [], indexed)
  const lastIndex = indexed.at(-1)?.value ?? null
  const lastBenchmark = benchmark.at(-1)?.value ?? null
  /* BTC 일봉 롤업이 덜 됐으면 비교선이 전략보다 늦게 시작한다. 왼쪽이 비는 이유를 적어 버그로 읽히지 않게 한다. */
  const benchmarkStartsLate = benchmark.length > 0 && indexed.length > 0 && benchmark[0].at.getTime() > indexed[0].at.getTime()
  const simulationEntry: SimulationEntryState = { strategyId: run.strategyId }
  /* 요청 시작일이 캔들 보유 시작일보다 앞서면 그 앞은 평가되지 않았다. 연평균 수익률 해석이 달라져 알린다. */
  const coverageStart = coverage.data?.start ?? null
  const startsBeforeCoverage = coverageStart !== null && formatDate(run.periodStart) < coverageStart

  return (
    <div className="bt-res on">
      {/* 폼 값을 바꿔도 이 결과가 어떤 조건이었는지 여기서 읽는다. 전략 이름은 실행이 가리키는 전략이다. */}
      <div className="result-context">
        <span>
          실행 전략{' '}
          <b>
            <StrategyName label={strategyName} />
          </b>
        </span>
        <span className="mono">
          {formatDate(run.periodStart)} ~ {formatDate(run.periodEnd)} · 초기자산 {formatKrw(run.initialCapital)} KRW
        </span>
      </div>

      <ResultKpis metrics={metrics} initialCapital={run.initialCapital} drawdownAt={peakDrawdownAt(points)} />

      {/* 커버리지는 결과와 다른 쿼리라 늦게 온다. KPI 위에 끼어들면 숫자를 읽는 중에 화면이 밀려 아래에 둔다. */}
      {startsBeforeCoverage && (
        <div className="callout warn" role="note" style={{ marginBottom: 16 }}>
          <span>
            요청 기간은 {formatDate(run.periodStart)}부터지만 캔들은 <b>{coverageStart}</b>부터 있어 그 이후만 평가됐습니다. 연평균 수익률은 요청 기간
            전체로 나눈 값이라 실제보다 낮게 보일 수 있습니다.
          </span>
        </div>
      )}

      <section className="card card--lead perf-stack">
        <div className="perf-head">
          <div>
            <div className="sec-title">누적 수익</div>
            <div className="sec-sub">
              {formatDate(run.periodStart)} ~ {formatDate(run.periodEnd)}
              {cycles.length > 0 && ` · ${cycles.length}회 리밸런싱 · 점은 종목이 교체된 회차`}
            </div>
          </div>
          <div className="perf-head-r">
            <div className="legend">
              <span>
                <i style={{ background: 'var(--ink)' }} />
                전략
              </span>
              {benchmark.length > 0 && (
                <span>
                  <i style={{ background: 'var(--ink-3)' }} />
                  BTC 단순보유
                </span>
              )}
            </div>
          </div>
        </div>
        {equity.isPending ? (
          <div className="perf-plot">
            <LoadingState label="자산 곡선을 불러오는 중…" />
          </div>
        ) : equity.isError ? (
          <div className="perf-plot">
            <ErrorState title="자산 곡선을 불러오지 못했습니다" error={equity.error} onRetry={() => equity.refetch()} />
          </div>
        ) : indexed.length === 0 ? (
          <div className="perf-plot">
            <p className="goal-note">평가 시점 기록이 없어 곡선을 그릴 수 없습니다.</p>
          </div>
        ) : (
          <PerformanceChart indexed={indexed} benchmark={benchmark} cycles={cycles} />
        )}
        <div className="perf-foot">
          <span>
            {benchmark.length > 0 ? (
              <>
                전략 선이 점선 위에 있는 구간은 BTC 단순보유보다 <b>초과 수익</b>, 아래는 열위입니다.
                {benchmarkStartsLate && <> BTC 일봉이 <b>{formatDate(benchmark[0].at)}</b>부터 있어 비교는 그날부터입니다.</>}
              </>
            ) : (
              <>BTC 봉을 받지 못해 단순보유 비교는 생략했습니다.</>
            )}
          </span>
          <span className="mono">
            전략 {lastIndex === null ? blank : formatIndex(lastIndex)} · BTC {lastBenchmark === null ? blank : formatIndex(lastBenchmark)} · 연평균{' '}
            {formatRate(metrics.cagr, { signed: true })}
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
        <Link className="btn primary" to={routes.simulations} state={simulationEntry}>
          이 전략으로 모의투자
        </Link>
      </div>
    </div>
  )
}
