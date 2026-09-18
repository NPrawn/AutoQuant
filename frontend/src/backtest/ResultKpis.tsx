import { formatDate, formatDrawdown, formatKrw, formatRate, formatRatio, trendClass } from './labels'
import type { BacktestResult } from './types'

type ResultKpisProps = {
  metrics: BacktestResult
  initialCapital: number
  /* 낙폭이 가장 깊었던 날. 자산 곡선을 받지 않은 화면은 비워 둔다. */
  drawdownAt?: Date | null
}

/* 목업 12 의 KPI 다섯 타일. 백테스트 결과 화면과 전략 성과 탭이 같은 타일을 쓴다. */
export function ResultKpis({ metrics, initialCapital, drawdownAt = null }: ResultKpisProps) {
  const profit = metrics.finalAssetValue - initialCapital

  return (
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
  )
}
