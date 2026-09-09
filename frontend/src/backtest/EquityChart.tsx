import { useEffect, useRef } from 'react'
import { AreaSeries, ColorType, createChart } from 'lightweight-charts'
import type { ISeriesApi, UTCTimestamp } from 'lightweight-charts'

import type { EquityPoint } from './types'

/* 자산 곡선. 캔들 차트와 같은 방식으로 테마 토큰을 읽고 data-theme 이 바뀌면 다시 적용한다. */
export function EquityChart({ points }: { points: EquityPoint[] }) {
  const container = useRef<HTMLDivElement>(null)
  const series = useRef<ISeriesApi<'Area'>>(null)
  const fitted = useRef(false)
  const fit = useRef<() => void>(() => {})

  useEffect(() => {
    if (!container.current) {
      return
    }
    const chart = createChart(container.current, {
      autoSize: true,
      timeScale: { timeVisible: false, secondsVisible: false },
      rightPriceScale: { borderVisible: false },
    })
    const areaSeries = chart.addSeries(AreaSeries, { lineWidth: 2, priceLineVisible: false })
    series.current = areaSeries
    fit.current = () => chart.timeScale().fitContent()
    fitted.current = false

    const applyTheme = () => {
      const style = getComputedStyle(document.documentElement)
      const token = (name: string) => style.getPropertyValue(name).trim()
      chart.applyOptions({
        layout: { background: { type: ColorType.Solid, color: token('--panel') }, textColor: token('--ink-2'), attributionLogo: true },
        grid: { vertLines: { color: token('--line') }, horzLines: { color: token('--line') } },
      })
      areaSeries.applyOptions({ lineColor: token('--ink'), topColor: token('--accent-tint'), bottomColor: 'transparent' })
    }
    applyTheme()
    const observer = new MutationObserver(applyTheme)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    return () => {
      observer.disconnect()
      chart.remove()
      series.current = null
    }
  }, [])

  useEffect(() => {
    series.current?.setData(
      points.map((point) => ({
        time: Math.floor(point.valuationAt.getTime() / 1000) as UTCTimestamp,
        value: point.totalAssetValue,
      })),
    )
    if (points.length > 0 && !fitted.current) {
      fit.current()
      fitted.current = true
    }
  }, [points])

  return <div ref={container} className="backtest-equity-chart" role="img" aria-label="평가 기간의 자산 곡선" />
}
