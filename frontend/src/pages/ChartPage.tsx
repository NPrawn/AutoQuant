import { lazy, Suspense, useState } from 'react'
import { LoadingState } from '../components/LoadingState'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { useChartAssets, useMarketFeed } from '../market/queries'
import type { ChartAsset } from '../market/types'
import { CANDLE_INTERVALS, intervalLabel } from '../market/model'
import type { CandleInterval } from '../market/model'

const MinuteCandleChart = lazy(() => import('../market/MinuteCandleChart').then((module) => ({ default: module.MinuteCandleChart })))

export function ChartPage() {
  const assets = useChartAssets()
  const [selectedId, setSelectedId] = useState('')
  const [interval, setInterval] = useState<CandleInterval>(1)
  if (assets.isPending) return <LoadingState label="종목을 불러오는 중…" />
  if (assets.isError) return <ErrorState error={assets.error} onRetry={() => void assets.refetch()} />
  const selected = assets.data.find((asset) => asset.id === selectedId) ?? assets.data[0]
  if (!selected) return <EmptyState title="등록된 종목이 없습니다" description="관리자가 Upbit KRW 종목을 등록하면 표시됩니다." />
  return (
    <div className="market-chart-page">
      <div className="chart-bar card">
        <label htmlFor="chart-market">종목</label>
        <select id="chart-market" value={selected.id} onChange={(event) => setSelectedId(event.target.value)}>
          {assets.data.map((asset) => <option key={asset.id} value={asset.id}>{asset.name} · {asset.marketCode}</option>)}
        </select>
        <label htmlFor="chart-interval">봉 간격</label>
        <select id="chart-interval" value={interval} onChange={(event) => setInterval(Number(event.target.value) as CandleInterval)}>
          {CANDLE_INTERVALS.map((value) => <option key={value} value={value}>{intervalLabel(value)}</option>)}
        </select>
        <span>최근 최대 1,000개 · UTC 기준</span>
      </div>
      <MarketChart key={`${selected.id}:${interval}`} asset={selected} interval={interval} />
    </div>
  )
}

function MarketChart({ asset, interval }: { asset: ChartAsset; interval: CandleInterval }) {
  const { history, connected, candles, trades } = useMarketFeed(asset, interval)
  const latestTrade = trades[0]
  return (
    <div className="chart-shell market-chart-shell">
      <section className="card">
        <div className="tick-head">
          <div>
            <div className="tick-name"><b>{asset.name}</b> {asset.marketCode}</div>
            <div className="tick-px">{latestTrade?.price ?? '—'} <small>KRW</small></div>
            <span>{latestTrade ? '마지막 수신 체결가' : '실시간 체결 대기 중'}</span>
          </div>
          <span role="status">{connected ? '실시간 연결됨' : '연결 중 · 끊기면 자동 재연결'}</span>
        </div>
        {history.isPending ? <LoadingState label={connected ? '최근 봉을 불러오는 중…' : '실시간 연결을 기다리는 중…'} />
          : history.isError ? <ErrorState error={history.error} onRetry={() => void history.refetch()} />
            : candles.length === 0 ? <EmptyState title="아직 봉 데이터가 없습니다" description="진행 봉이 도착하면 차트가 표시됩니다." />
              : <Suspense fallback={<LoadingState label="차트를 준비하는 중…" />}><MinuteCandleChart candles={candles} /></Suspense>}
        <p className="market-chart-note">체결 없는 시간은 빈 구간입니다. 진행 봉은 집계 주기에 따라 갱신됩니다.</p>
        <p className="market-chart-note">차트: <a href="https://www.tradingview.com/" target="_blank" rel="noreferrer">TradingView Lightweight Charts™</a></p>
      </section>
      <section className="card market-trades">
        <h2>최근 체결</h2>
        <p>현재 연결에서 수신한 최근 30건</p>
        {trades.length === 0 ? <EmptyState title="체결 대기 중" /> : (
          <div className="market-trade-scroll"><table>
            <thead><tr><th>시간</th><th>가격(KRW)</th><th>수량</th><th>구분</th></tr></thead>
            <tbody>{trades.map((trade) => (
              <tr key={trade.sequenceId} className={trade.side === 'BID' ? 'up' : 'down'}>
                <td className="num">{new Date(trade.tradedAtUnixMilli).toLocaleTimeString('ko-KR', { hour12: false })}</td>
                <td className="num">{trade.price}</td><td className="num">{trade.volume}</td><td>{trade.side === 'BID' ? '매수' : '매도'}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </section>
    </div>
  )
}
