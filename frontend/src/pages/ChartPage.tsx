import { lazy, Suspense, useMemo, useState } from 'react'

import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { CoinList } from '../market/CoinList'
import { CANDLE_INTERVALS, intervalLabel } from '../market/model'
import type { CandleInterval } from '../market/model'
import { useChartAssets, useDayStats, useLivePrices, useMarketFeed, useMarketTickers } from '../market/queries'
import { changeClass, changeRate, formatChangeRate, formatPrice, formatVolume } from '../market/ticker'
import type { Ticker } from '../market/ticker'
import type { CandleResponse, ChartAsset, Trade } from '../market/types'

const MinuteCandleChart = lazy(() => import('../market/MinuteCandleChart').then((module) => ({ default: module.MinuteCandleChart })))

/*
 * 차트(목업 04). 좌측에서 종목을 고르고 가운데에서 봉을, 우측에서 체결을 본다.
 * 좌측 목록의 현재가는 전 종목 체결 구독으로, 가운데 봉과 체결은 선택 종목 구독으로 온다.
 * 두 구독을 나눈 이유는 목록이 종목을 바꿔도 끊기면 안 되고, 봉은 선택이 바뀔 때마다 새로 받아야 하기 때문이다.
 */
export function ChartPage() {
  const assets = useChartAssets()
  const [selectedId, setSelectedId] = useState('')
  const [interval, setInterval] = useState<CandleInterval>(60)
  const [query, setQuery] = useState('')

  const list = useMemo(() => assets.data ?? [], [assets.data])
  const tickers = useMarketTickers(list)
  const { prices } = useLivePrices(list)

  if (assets.isPending) {
    return <LoadingState label="종목을 불러오는 중…" />
  }
  if (assets.isError) {
    return <ErrorState title="종목을 불러오지 못했습니다" error={assets.error} onRetry={() => void assets.refetch()} />
  }

  const selected = list.find((asset) => asset.id === selectedId) ?? list[0]
  if (!selected) {
    return <EmptyState title="등록된 종목이 없습니다" description="관리자가 Upbit KRW 종목을 등록하면 표시됩니다." />
  }

  return (
    <section className="view active" id="view-chart">
      <div className="page-head">
        <div>
          <span className="eyebrow">시세</span>
          <h1>차트</h1>
        </div>
      </div>

      <div className="chart-shell">
        <div className="card" style={{ padding: 8 }}>
          <CoinList
            assets={list}
            tickers={tickers.data}
            prices={prices}
            selectedId={selected.id}
            query={query}
            onQueryChange={setQuery}
            onSelect={setSelectedId}
          />
        </div>

        <MarketChartCard
          key={`${selected.id}:${interval}`}
          asset={selected}
          interval={interval}
          ticker={tickers.data?.get(selected.id)}
          livePrice={prices.get(selected.id)?.price ?? null}
          onIntervalChange={setInterval}
        />
      </div>
    </section>
  )
}

type MarketChartCardProps = {
  asset: ChartAsset
  interval: CandleInterval
  ticker: Ticker | undefined
  livePrice: string | null
  onIntervalChange: (interval: CandleInterval) => void
}

function MarketChartCard({ asset, interval, ticker, livePrice, onIntervalChange }: MarketChartCardProps) {
  const { history, connected, candles, rawCandles, trades } = useMarketFeed(asset, interval)
  const dayStats = useDayStats(asset.id)

  const latestCandle = rawCandles.at(-1) ?? null
  const price = livePrice ?? latestCandle?.closePrice ?? ticker?.lastClose ?? null
  const rate = changeRate(price, ticker?.previousClose ?? null)

  return (
    <>
      <div className="card" style={{ overflow: 'hidden' }}>
        <div className="tick-head">
          <div>
            <div className="tick-name">
              <b>{asset.marketCode}</b>
              <span>{asset.name}</span>
            </div>
            <div className={`tick-px ${changeClass(rate)}`}>{formatPrice(price)}</div>
            <div className={`tick-ch ${changeClass(rate)}`}>
              {formatChangeRate(rate)}
              <span style={{ color: 'var(--ink-3)', marginLeft: 8 }}>{connected ? '실시간 연결됨' : '연결 중 · 끊기면 자동 재연결'}</span>
            </div>
          </div>
          <div className="tick-stats">
            <div className="it">
              <span className="k">24h 고가</span>
              <span className="v">{formatPrice(dayStats.data?.high ?? null)}</span>
            </div>
            <div className="it">
              <span className="k">24h 저가</span>
              <span className="v">{formatPrice(dayStats.data?.low ?? null)}</span>
            </div>
            <div className="it">
              <span className="k">24h 거래량</span>
              <span className="v">{dayStats.data ? formatVolume(dayStats.data.volume) : '—'}</span>
            </div>
          </div>
        </div>

        <div className="chart-bar">
          <div className="tfs" role="group" aria-label="봉 간격">
            {CANDLE_INTERVALS.map((value) => (
              <button
                key={value}
                type="button"
                className={value === interval ? 'on' : undefined}
                aria-pressed={value === interval}
                onClick={() => onIntervalChange(value)}
              >
                {intervalLabel(value)}
              </button>
            ))}
          </div>
          <span style={{ color: 'var(--ink-3)', fontSize: 11 }}>최근 최대 1,000개 · UTC 기준</span>
        </div>

        <div className="chart-area">
          <div className="chart-legend">
            <div className="l1">
              <ChartLegend asset={asset} interval={interval} candle={latestCandle} />
            </div>
          </div>
          {history.isPending ? (
            <LoadingState label={connected ? '최근 봉을 불러오는 중…' : '실시간 연결을 기다리는 중…'} />
          ) : history.isError ? (
            <ErrorState title="봉을 불러오지 못했습니다" error={history.error} onRetry={() => void history.refetch()} />
          ) : candles.length === 0 ? (
            <EmptyState title="아직 봉 데이터가 없습니다" description="진행 봉이 도착하면 차트가 표시됩니다." />
          ) : (
            <Suspense fallback={<LoadingState label="차트를 준비하는 중…" />}>
              <MinuteCandleChart candles={candles} />
            </Suspense>
          )}
        </div>
      </div>

      <div>
        <section className="card pad">
          <div className="sec-title" style={{ marginBottom: 2 }}>최근 체결</div>
          <div className="sec-sub" style={{ marginBottom: 12 }}>현재 연결에서 수신한 최근 30건</div>
          <TradeList trades={trades} />
        </section>
        <p className="goal-note" style={{ margin: '12px 4px 0' }}>
          체결 없는 시간은 빈 구간입니다. 진행 봉은 집계 주기에 따라 갱신됩니다.
          <br />
          차트: <a href="https://www.tradingview.com/" target="_blank" rel="noreferrer">TradingView Lightweight Charts™</a>
        </p>
      </div>
    </>
  )
}

/* 범례 첫 줄. 지표 값을 보이는 둘째 줄은 지표 작업에서 붙인다. */
function ChartLegend({ asset, interval, candle }: { asset: ChartAsset; interval: CandleInterval; candle: CandleResponse | null }) {
  if (candle === null) {
    return <b>{asset.marketCode}</b>
  }
  const rate = changeRate(candle.closePrice, Number(candle.openPrice))
  return (
    <>
      <b>{asset.marketCode}</b> · {intervalLabel(interval)} &nbsp; 시<span className={changeClass(rate)}>{formatPrice(candle.openPrice)}</span>
      {' '}고<span className={changeClass(rate)}>{formatPrice(candle.highPrice)}</span>
      {' '}저<span className={changeClass(rate)}>{formatPrice(candle.lowPrice)}</span>
      {' '}종<span className={changeClass(rate)}>{formatPrice(candle.closePrice)}</span>
      {' '}<span className={changeClass(rate)}>{formatChangeRate(rate)}</span>
    </>
  )
}

function TradeList({ trades }: { trades: Trade[] }) {
  if (trades.length === 0) {
    return <p className="goal-note" style={{ margin: 0 }}>체결을 기다리고 있습니다.</p>
  }
  return (
    <>
      <div className="tr-head">
        <span>시각</span>
        <span>구분</span>
        <span>체결가</span>
      </div>
      <div>
        {trades.map((trade) => {
          const bid = trade.side === 'BID'
          return (
            <div className="trr" key={trade.sequenceId}>
              <span className="t">{new Date(trade.tradedAtUnixMilli).toLocaleTimeString('ko-KR', { hour12: false })}</span>
              <span className={`s ${bid ? 'updown up' : 'updown down'}`}>{bid ? '매수' : '매도'}</span>
              <span className={`p ${bid ? 'updown up' : 'updown down'}`}>{formatPrice(trade.price)}</span>
            </div>
          )
        })}
      </div>
    </>
  )
}
