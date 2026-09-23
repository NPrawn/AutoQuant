import type { MouseEvent } from 'react'
import { Link } from 'react-router'

import { to } from '../app/routes'
import { blank, formatDate, formatKrw, formatRate, trendClass } from '../backtest/labels'
import { rebalanceOf, timeframeLabel } from '../strategy/labels'
import type { StrategyLabel } from '../strategy/lineage'
import { StrategyName } from '../strategy/StrategyName'
import { assetCode, type AssetLookup } from './assetLookup'
import { statusOf } from './labels'
import { useSimulationEquityCurve, useSimulationPositions, useSimulationResult } from './queries'
import { sparkFromEquity, sparkHeight, sparkWidth } from './sparkline'
import type { SimulationRun } from './types'

type SimulationCardProps = {
  run: SimulationRun
  strategyName: StrategyLabel
  lookup: AssetLookup
  onOpen: () => void
}

/*
 * 목업 16 의 실행 카드. 평가자산과 수익률은 마지막 봉 기준 결과에서, 보유 종목은 포지션에서 온다.
 * 첫 봉이 마감되기 전에는 결과가 없어 빈 값이다. 카드 전체가 클릭 영역이고 제목 링크가 키보드 진입점이다.
 */
export function SimulationCard({ run, strategyName, lookup, onOpen }: SimulationCardProps) {
  const live = run.status === 'RUNNING'
  /* 카드는 한 번만 읽는다. 카드마다 15초 폴링이면 12건이면 15초에 36요청이다. 실시간 갱신은 상세 화면이 한다. */
  const result = useSimulationResult(run.id, false)
  const positions = useSimulationPositions(run.id, false)
  const status = statusOf(run.status)
  const metrics = result.data ?? null
  const held = positions.data ?? []

  const handleClick = (event: MouseEvent<HTMLElement>) => {
    if ((event.target as HTMLElement).closest('a, button')) {
      return
    }
    onOpen()
  }

  return (
    <article className="card pad sim-strategy-card" onClick={handleClick}>
      <div className="simh">
        <div>
          <h4>
            <Link to={to.simulationLive(run.id)}>
              <StrategyName label={strategyName} />
            </Link>
          </h4>
          <div className="m">
            {timeframeLabel(run.timeframe)} 종가 · {rebalanceOf(run.rebalanceInterval).label} 재선정
            {run.startedAt !== null && ` · ${formatDate(run.startedAt)} 시작`}
          </div>
        </div>
        <span className={status.chip}>{status.label}</span>
      </div>
      <EquitySpark simulationId={run.id} />
      <div className="sims">
        <div>
          <span className="k">평가자산</span>
          <span className="v mono">{metrics === null ? blank : formatKrw(metrics.assetValue)}</span>
        </div>
        <div>
          <span className="k">수익률</span>
          <span className={`v mono ${metrics === null ? '' : trendClass(metrics.cumulativeReturn)}`}>
            {metrics === null ? blank : formatRate(metrics.cumulativeReturn, { signed: true })}
          </span>
        </div>
        <div>
          <span className="k">보유 종목 {held.length}</span>
          <span className="v sim-last-signal">{held.length === 0 ? '현금 대기' : held.map((position) => assetCode(lookup, position.assetId)).join(' · ')}</span>
        </div>
      </div>
      <div className="sim-open">
        {live ? '실시간 운용 보기' : '기록 보기'} <span aria-hidden="true">→</span>
      </div>
    </article>
  )
}

/*
 * 카드 안 수익 곡선. 평가 시점이 둘 미만이면 그릴 모양이 없어 자리를 비운다.
 * 곡선은 마지막 봉 기준 평가자산이라 실시간 체결보다 늦게 움직인다.
 */
function EquitySpark({ simulationId }: { simulationId: string }) {
  const curve = useSimulationEquityCurve(simulationId, false)
  const spark = sparkFromEquity(curve.data ?? [])
  if (spark === null) {
    /* 자리는 남긴다. 첫 봉이 마감돼 곡선이 생기는 순간 카드가 커지면 아래 카드가 밀린다. */
    return (
      <div className="sim-chart">
        <div className="sim-chart-head">
          <span>운용 수익 곡선</span>
          <span>{curve.data?.length ?? 0}개 평가 시점</span>
        </div>
        <div className="spark spark-empty">첫 봉이 마감되면 곡선이 그려집니다</div>
      </div>
    )
  }

  const gradientId = `spark-${simulationId}`
  const tone = spark.rising ? 'var(--up)' : 'var(--down)'
  return (
    <div className="sim-chart">
      <div className="sim-chart-head">
        <span>운용 수익 곡선</span>
        <span>{curve.data?.length ?? 0}개 평가 시점</span>
      </div>
      <svg className="spark" viewBox={`0 0 ${sparkWidth} ${sparkHeight}`} role="img" aria-label="운용 수익 곡선">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor={tone} stopOpacity=".24" />
            <stop offset="1" stopColor={tone} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path className="sim-grid" d="M0 35H600M0 70H600M0 105H600" />
        <path d={spark.area} fill={`url(#${gradientId})`} />
        <path className="sim-line" d={spark.line} style={{ stroke: tone }} />
        <circle className="sim-point" cx={spark.lastX} cy={spark.lastY} r="4" style={{ fill: tone }} />
      </svg>
    </div>
  )
}
