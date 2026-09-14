import { useEffect, useRef } from 'react'
import { CandlestickSeries, ColorType, HistogramSeries, LineSeries, createChart } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, UTCTimestamp } from 'lightweight-charts'

import { movingAveragePeriods, type IndicatorData, type IndicatorKey, type IndicatorPoint } from './indicators'
import type { Candle } from './types'

type MinuteCandleChartProps = {
  candles: Candle[]
  /* 지표 값은 범례와 함께 쓰므로 밖에서 한 번만 계산해 받는다. */
  data: IndicatorData
  /* 켜 둔 지표. 꺼진 지표는 시리즈를 비워 두고 패널도 접는다. */
  indicators: ReadonlySet<IndicatorKey>
}

const toTime = (time: number) => time as UTCTimestamp
const line = (points: readonly IndicatorPoint[]) => points.map((point) => ({ time: toTime(point.time), value: point.value }))

/*
 * 봉 차트와 보조지표. 지표는 같은 창의 별도 패널에 둔다. 거래량과 RSI 는 가격과 축이 달라
 * 같은 패널에 그리면 봉이 눌린다.
 */
export function MinuteCandleChart({ candles, data, indicators }: MinuteCandleChartProps) {
  const container = useRef<HTMLDivElement>(null)
  const chart = useRef<IChartApi>(null)
  const price = useRef<ISeriesApi<'Candlestick'>>(null)
  const averages = useRef<ISeriesApi<'Line'>[]>([])
  const bands = useRef<ISeriesApi<'Line'>[]>([])
  const volume = useRef<ISeriesApi<'Histogram'>>(null)
  const strength = useRef<ISeriesApi<'Line'>>(null)
  const fitted = useRef(false)

  useEffect(() => {
    if (!container.current) return
    const created = createChart(container.current, { autoSize: true, timeScale: { timeVisible: true, secondsVisible: false } })
    chart.current = created
    fitted.current = false

    price.current = created.addSeries(CandlestickSeries, { borderVisible: false })
    /* 패널 1 은 거래량, 패널 2 는 RSI 다. 순서를 바꾸면 축 눈금이 뒤섞인다. */
    volume.current = created.addSeries(HistogramSeries, { priceFormat: { type: 'volume' }, priceLineVisible: false }, 1)
    strength.current = created.addSeries(LineSeries, { lineWidth: 1, priceLineVisible: false }, 2)
    averages.current = movingAveragePeriods.map(() => created.addSeries(LineSeries, { lineWidth: 1, priceLineVisible: false, lastValueVisible: false }))
    bands.current = [0, 1, 2].map(() => created.addSeries(LineSeries, { lineWidth: 1, priceLineVisible: false, lastValueVisible: false }))

    const applyTheme = () => {
      const style = getComputedStyle(document.documentElement)
      const token = (name: string) => style.getPropertyValue(name).trim()
      created.applyOptions({
        layout: { background: { type: ColorType.Solid, color: token('--panel') }, textColor: token('--ink-2'), attributionLogo: true },
        grid: { vertLines: { color: token('--line') }, horzLines: { color: token('--line') } },
      })
      price.current?.applyOptions({ upColor: token('--up'), downColor: token('--down'), wickUpColor: token('--up'), wickDownColor: token('--down') })
      averages.current.forEach((series, index) => series.applyOptions({ color: token(`--ma${movingAveragePeriods[index]}`) }))
      bands.current.forEach((series) => series.applyOptions({ color: token('--bb') }))
      strength.current?.applyOptions({ color: token('--accent') })
    }
    applyTheme()
    const observer = new MutationObserver(applyTheme)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    return () => {
      observer.disconnect()
      created.remove()
      chart.current = null
      price.current = null
      volume.current = null
      strength.current = null
      averages.current = []
      bands.current = []
    }
  }, [])

  useEffect(() => {
    price.current?.setData(
      candles.map((candle) => ({ time: toTime(candle.time), open: candle.open, high: candle.high, low: candle.low, close: candle.close })),
    )
    if (candles.length > 0 && !fitted.current) {
      chart.current?.timeScale().fitContent()
      fitted.current = true
    }
  }, [candles])

  useEffect(() => {
    const style = getComputedStyle(document.documentElement)
    const token = (name: string) => style.getPropertyValue(name).trim()
    const showMa = indicators.has('ma')
    const showBb = indicators.has('bb')
    const showVol = indicators.has('vol')
    const showRsi = indicators.has('rsi')

    averages.current.forEach((series, index) => series.setData(showMa ? line(data.averages[index]) : []))
    const bandPoints = [data.bands.upper, data.bands.middle, data.bands.lower]
    bands.current.forEach((series, index) => series.setData(showBb ? line(bandPoints[index]) : []))
    volume.current?.setData(
      showVol
        ? data.volume.map((bar) => ({ time: toTime(bar.time), value: bar.value, color: token(bar.rising ? '--up' : '--down') }))
        : [],
    )
    strength.current?.setData(showRsi ? line(data.strength) : [])

    /* 꺼진 패널은 높이를 0 으로 접어 봉이 넓게 보이게 한다. */
    const panes = chart.current?.panes() ?? []
    panes[1]?.setHeight(showVol ? 80 : 1)
    panes[2]?.setHeight(showRsi ? 80 : 1)
  }, [data, indicators])

  /* id 는 목업이 정한 차트 자리다. base.css 의 #view-chart #candle 이 크기를 맡는다. */
  return <div ref={container} id="candle" role="img" aria-label="선택 종목의 봉 차트" />
}
