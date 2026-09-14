import { useEffect, useRef } from 'react'
import { CandlestickSeries, ColorType, createChart } from 'lightweight-charts'
import type { ISeriesApi, UTCTimestamp } from 'lightweight-charts'
import type { Candle } from './types'

export function MinuteCandleChart({ candles }: { candles: Candle[] }) {
  const container = useRef<HTMLDivElement>(null)
  const series = useRef<ISeriesApi<'Candlestick'>>(null)
  const fitted = useRef(false)
  const fit = useRef<() => void>(() => {})

  useEffect(() => {
    if (!container.current) return
    const chart = createChart(container.current, { autoSize: true, timeScale: { timeVisible: true, secondsVisible: false } })
    const candleSeries = chart.addSeries(CandlestickSeries, { borderVisible: false })
    series.current = candleSeries
    fit.current = () => chart.timeScale().fitContent()
    fitted.current = false
    const applyTheme = () => {
      const style = getComputedStyle(document.documentElement)
      const token = (name: string) => style.getPropertyValue(name).trim()
      chart.applyOptions({
        layout: { background: { type: ColorType.Solid, color: token('--panel') }, textColor: token('--ink-2'), attributionLogo: true },
        grid: { vertLines: { color: token('--line') }, horzLines: { color: token('--line') } },
      })
      candleSeries.applyOptions({ upColor: token('--up'), downColor: token('--down'), wickUpColor: token('--up'), wickDownColor: token('--down') })
    }
    applyTheme()
    const observer = new MutationObserver(applyTheme)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => { observer.disconnect(); chart.remove(); series.current = null }
  }, [])

  useEffect(() => {
    series.current?.setData(candles.map((candle) => ({ ...candle, time: candle.time as UTCTimestamp })))
    if (candles.length > 0 && !fitted.current) { fit.current(); fitted.current = true }
  }, [candles])

  /* id 는 목업이 정한 차트 자리다. base.css 의 #view-chart #candle 이 크기를 맡는다. */
  return <div ref={container} id="candle" role="img" aria-label="선택 종목의 봉 차트" />
}
