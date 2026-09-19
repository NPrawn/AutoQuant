import { lazy, Suspense, useMemo } from 'react'

import { computeIndicators } from '../market/indicators'
import type { IndicatorKey } from '../market/indicators'
import type { CandleInterval } from '../market/model'
import { useCandleHistory, useChartAssets } from '../market/queries'
import { displayMarket } from '../market/ticker'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/StateBlock'
import type { Timeframe } from '../strategy/types'
import { useBacktestTradeMarkers } from './queries'
import { busiestMarket, markersFor } from './tradeMarkers'

const MinuteCandleChart = lazy(() => import('../market/MinuteCandleChart').then((module) => ({ default: module.MinuteCandleChart })))

/* 체결 위치를 읽는 차트다. 지표는 목업처럼 이동평균만 켜고 아래 패널은 두지 않는다. */
const chartIndicators: ReadonlySet<IndicatorKey> = new Set<IndicatorKey>(['ma'])

const timeframeIntervals: Record<Timeframe, CandleInterval> = { '1H': 60, '4H': 240, '1D': 1440 }

type TradeChartProps = {
  backtestId: string
  timeframe: Timeframe
}

/*
 * 목업 07 의 전략 체결 차트. 체결이 가장 많았던 종목의 봉 위에 매수, 매도 자리를 얹는다.
 * 봉은 최근 구간만 보관하므로 오래된 실행은 마커가 화면 밖에 있을 수 있다.
 */
export function TradeChart({ backtestId, timeframe }: TradeChartProps) {
  const trades = useBacktestTradeMarkers(backtestId, true)
  const assets = useChartAssets()

  const marketCode = busiestMarket(trades.data ?? [])
  const asset = (assets.data ?? []).find((candidate) => candidate.marketCode === marketCode) ?? null
  const interval = timeframeIntervals[timeframe]
  const candles = useCandleHistory(asset?.id ?? null, interval)

  const data = useMemo(() => computeIndicators(candles.data ?? []), [candles.data])
  const markers = useMemo(
    () => (marketCode === null ? [] : markersFor(trades.data ?? [], marketCode)),
    [trades.data, marketCode],
  )

  if (trades.isPending) {
    return <LoadingState label="체결을 불러오는 중…" />
  }
  if (trades.isError) {
    return <ErrorState title="체결을 불러오지 못했습니다" error={trades.error} onRetry={() => void trades.refetch()} />
  }
  if (marketCode === null) {
    return <p className="goal-note" style={{ margin: 0 }}>이 실행에는 체결이 없습니다.</p>
  }
  if (asset === null) {
    return <p className="goal-note" style={{ margin: 0 }}>{displayMarket(marketCode)} 은 더 이상 수집하지 않는 종목이라 봉을 그릴 수 없습니다.</p>
  }

  return (
    <>
      <div className="sec-sub" style={{ marginBottom: 12 }}>
        {displayMarket(asset.marketCode)} · 체결 {markers.length}건 · 이 실행에서 가장 자주 담은 종목입니다
      </div>
      {candles.isPending ? (
        <LoadingState label="봉을 불러오는 중…" />
      ) : candles.isError ? (
        <ErrorState title="봉을 불러오지 못했습니다" error={candles.error} onRetry={() => void candles.refetch()} />
      ) : (candles.data?.length ?? 0) === 0 ? (
        <p className="goal-note" style={{ margin: 0 }}>아직 이 종목의 봉이 없습니다.</p>
      ) : (
        <Suspense fallback={<LoadingState label="차트를 준비하는 중…" />}>
          <MinuteCandleChart candles={candles.data ?? []} data={data} indicators={chartIndicators} markers={markers} subPanes={false} />
        </Suspense>
      )}
    </>
  )
}
