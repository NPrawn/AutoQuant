import { useId, useLayoutEffect, useRef, useState } from 'react'

import { formatDate } from './labels'
import {
  chartFrame,
  closedPath,
  equityDomain,
  formatIndex,
  formatMonth,
  linePath,
  linearScale,
  monthTicks,
  niceTicks,
  timeScale,
} from './performance'
import type { IndexedPoint, RebalanceCycle } from './performance'

type PerformanceChartProps = {
  indexed: readonly IndexedPoint[]
  benchmark: readonly IndexedPoint[]
  cycles: readonly RebalanceCycle[]
  label?: string
}

type Hover = { cycle: RebalanceCycle; cx: number; cy: number }

const mono = 'IBM Plex Mono,monospace'

/*
 * 목업 12 의 성과 차트에서 위 구간만 가져왔다. 지수 100 기준 전략과 단순보유를 한 눈금에 놓는다.
 * 리밸런싱 회차마다 점을 찍고, 점을 가리키면 그 회차의 매수·매도와 수익을 담은 카드가 점 위에 뜬다.
 * 계산은 performance.ts 가 하고 여기서는 좌표를 SVG 로 옮기기만 한다.
 */
export function PerformanceChart({ indexed, benchmark, cycles, label = '누적 수익' }: PerformanceChartProps) {
  const id = useId()
  const wrap = useRef<HTMLDivElement>(null)
  const svg = useRef<SVGSVGElement>(null)
  const tip = useRef<HTMLDivElement>(null)
  const [hover, setHover] = useState<Hover | null>(null)
  const [tipPosition, setTipPosition] = useState({ left: 0, top: 0 })

  /* 카드는 점 위에 뜬다. 자리가 없으면 아래로 내리고, 좌우로 넘치면 안으로 밀어 넣는다. 목업의 규칙 그대로다. */
  useLayoutEffect(() => {
    if (hover === null || !wrap.current || !svg.current || !tip.current) {
      return
    }
    const wrapRect = wrap.current.getBoundingClientRect()
    const svgRect = svg.current.getBoundingClientRect()
    const px = svgRect.left - wrapRect.left + (hover.cx / chartFrame.width) * svgRect.width
    const py = svgRect.top - wrapRect.top + (hover.cy / chartFrame.height) * svgRect.height
    const width = tip.current.offsetWidth
    const height = tip.current.offsetHeight
    setTipPosition({
      left: Math.max(4, Math.min(wrapRect.width - width - 4, px - width / 2)),
      top: py - height - 14 >= 4 ? py - height - 14 : Math.min(wrapRect.height - height - 4, py + 16),
    })
  }, [hover])

  const first = indexed[0]
  const last = indexed[indexed.length - 1]
  if (first === undefined || last === undefined) {
    return null
  }

  const x = timeScale(indexed)
  const domain = equityDomain([indexed, benchmark])
  const yEquity = linearScale(domain.lo, domain.hi, chartFrame.equityBottom, chartFrame.equityTop)

  const strategyLine = indexed.map((point) => ({ x: x(point.at.getTime()), y: yEquity(point.value) }))
  const benchmarkLine = benchmark.map((point) => ({ x: x(point.at.getTime()), y: yEquity(point.value) }))
  const lastBenchmark = benchmark[benchmark.length - 1]
  const clipAbove = `${id}-above`
  const clipBelow = `${id}-below`

  return (
    <div ref={wrap} className="perf-plot" style={{ position: 'relative' }}>
      <svg
        ref={svg}
        viewBox={`0 0 ${chartFrame.width} ${chartFrame.height}`}
        style={{ width: '100%', height: 'auto', overflow: 'visible' }}
        role="img"
        aria-label={label}
      >
        <defs>
          {/* 전략 선 위·아래를 한 면으로 잘라 둔다. 단순보유와 견준 초과·열위 구간을 이 면으로 걸러 칠한다. */}
          <clipPath id={clipAbove}>
            <path d={closedPath(strategyLine, chartFrame.equityTop)} />
          </clipPath>
          <clipPath id={clipBelow}>
            <path d={closedPath(strategyLine, chartFrame.equityBottom)} />
          </clipPath>
        </defs>

        {niceTicks(domain.lo, domain.hi, 6).map((tick) => (
          <g key={tick}>
            <line x1={chartFrame.left} y1={yEquity(tick)} x2={chartFrame.right} y2={yEquity(tick)} stroke="var(--grid)" />
            <text x={chartFrame.left - 6} y={yEquity(tick) + 3.5} textAnchor="end" fill="var(--ink-3)" fontSize="10" fontFamily={mono}>
              {tick}
            </text>
          </g>
        ))}
        {monthTicks(first.at, last.at).map((month) => (
          <g key={month.getTime()}>
            <line x1={x(month.getTime())} y1={chartFrame.equityTop} x2={x(month.getTime())} y2={chartFrame.equityBottom} stroke="var(--grid)" />
            <text x={x(month.getTime())} y={chartFrame.axisLabelY} textAnchor="middle" fill="var(--ink-3)" fontSize="10" fontFamily={mono}>
              {formatMonth(month)}
            </text>
          </g>
        ))}

        {benchmarkLine.length > 1 && (
          <>
            <path d={closedPath(benchmarkLine, chartFrame.equityTop)} fill="var(--up)" opacity=".16" clipPath={`url(#${clipBelow})`} />
            <path d={closedPath(benchmarkLine, chartFrame.equityBottom)} fill="var(--down)" opacity=".16" clipPath={`url(#${clipAbove})`} />
            <path d={linePath(benchmarkLine)} fill="none" stroke="var(--ink-3)" strokeWidth="1.4" strokeDasharray="4 3" />
          </>
        )}
        <path d={linePath(strategyLine)} fill="none" stroke="var(--ink)" strokeWidth="2" />

        <text x={chartFrame.left} y={chartFrame.equityTop + 12} fill="var(--ink-3)" fontSize="10">
          누적 수익 · 지수 100 시작
        </text>

        <line
          className="rb-cursor"
          x1={hover?.cx ?? 0}
          y1={chartFrame.equityTop}
          x2={hover?.cx ?? 0}
          y2={chartFrame.equityBottom}
          stroke="var(--line-2)"
          opacity={hover === null ? 0 : 1}
        />

        <circle cx={x(last.at.getTime())} cy={yEquity(last.value)} r="2.6" fill="var(--ink)" />
        <text x={x(last.at.getTime()) + 6} y={yEquity(last.value) + 3.5} fill="var(--ink)" fontSize="11" fontWeight="600" fontFamily={mono}>
          {formatIndex(last.value)}
        </text>
        {lastBenchmark !== undefined && (
          <>
            <circle cx={x(lastBenchmark.at.getTime())} cy={yEquity(lastBenchmark.value)} r="2.6" fill="var(--ink-3)" />
            <text x={x(lastBenchmark.at.getTime()) + 6} y={yEquity(lastBenchmark.value) + 3.5} fill="var(--ink-3)" fontSize="11" fontFamily={mono}>
              {formatIndex(lastBenchmark.value)}
            </text>
          </>
        )}

        {cycles.map((cycle) => {
          const cx = x(cycle.at.getTime())
          const cy = yEquity(cycle.equityIndex)
          const show = () => setHover({ cycle, cx, cy })
          const hide = () => setHover(null)
          return (
            <g
              key={cycle.index}
              className="rb-mark"
              tabIndex={0}
              role="button"
              aria-label={`${cycle.index + 1}회차 ${formatDate(cycle.at)}`}
              onMouseEnter={show}
              onMouseLeave={hide}
              onFocus={show}
              onBlur={hide}
            >
              <circle cx={cx} cy={cy} r="10" fill="transparent" />
              <circle className="dot" cx={cx} cy={cy} r="3.4" fill="var(--bg)" stroke="var(--ink)" strokeWidth="1.6" />
            </g>
          )
        })}
      </svg>

      <div ref={tip} className={hover === null ? 'rb-tip' : 'rb-tip on'} style={tipPosition} role="tooltip" aria-hidden={hover === null}>
        {hover !== null && <CycleCard cycle={hover.cycle} />}
      </div>
    </div>
  )
}

function CycleCard({ cycle }: { cycle: RebalanceCycle }) {
  const gain = cycle.returnRate >= 0
  return (
    <>
      <b>
        {cycle.index + 1}회차 <span>{formatDate(cycle.at)}</span>
      </b>
      <div className="r">
        <span>매수</span>
        <em className="in">{cycle.bought.length > 0 ? cycle.bought.join(' · ') : '없음'}</em>
      </div>
      <div className="r">
        <span>매도</span>
        <em className="out">{cycle.sold.length > 0 ? cycle.sold.join(' · ') : '없음'}</em>
      </div>
      <div className="r">
        <span>회차 수익</span>
        <em className={gain ? 'updown up' : 'updown down'}>
          {gain ? '+' : ''}
          {cycle.returnRate.toFixed(2)}%
        </em>
      </div>
      <div className="r">
        <span>누적</span>
        <em>{formatIndex(cycle.equityIndex)}</em>
      </div>
    </>
  )
}
