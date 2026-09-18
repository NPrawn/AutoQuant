import { useEffect, useRef } from 'react'
import { AreaSeries, CandlestickSeries, ColorType, HistogramSeries, LineSeries, LineStyle, createChart, createSeriesMarkers, createTextWatermark } from 'lightweight-charts'
import type { IChartApi, ISeriesApi, ISeriesMarkersPluginApi, ITextWatermarkPluginApi, Time, UTCTimestamp } from 'lightweight-charts'

import { movingAveragePeriods, rsiPeriod, type IndicatorData, type IndicatorKey, type IndicatorPoint } from './indicators'
import type { TradeMarker } from '../backtest/tradeMarkers'
import type { Candle } from './types'

type MinuteCandleChartProps = {
  candles: Candle[]
  /* 지표 값은 범례와 함께 쓰므로 밖에서 한 번만 계산해 받는다. */
  data: IndicatorData
  /* 켜 둔 지표. 꺼진 지표는 시리즈를 비워 두고 패널도 접는다. */
  indicators: ReadonlySet<IndicatorKey>
  /* 봉 위에 찍을 체결. 지난 실행을 되짚는 화면만 넘긴다. */
  markers?: readonly TradeMarker[]
}

const toTime = (time: number) => time as UTCTimestamp
const line = (points: readonly IndicatorPoint[]) => points.map((point) => ({ time: toTime(point.time), value: point.value }))

/*
 * 봉 하나가 차지할 가로 픽셀이다. 보유한 1,000 개를 한 화면에 맞추면 봉이 실선이 되므로
 * 굵기를 먼저 정하고 최근 구간만 보여 준다. 과거는 좌우 스크롤로 본다.
 */
const barSpacing = 10
/* 가격 축 폭. 1억 단위 BTC 라벨 "106,901,000" 이 들어가는 폭이라 종목이 바뀌어도 봉 영역이 같다. */
const priceScaleWidth = 84
/* 거래량·RSI 패널 높이. 꺼도 접지 않고 자리를 지켜 봉 패널 크기가 바뀌지 않는다. */
const subPaneHeight = 88
const paneTitles = ['거래량', `RSI ${rsiPeriod}`] as const

function paneLabelText(index: number, off: boolean): string {
  return off ? `${paneTitles[index]} · 꺼짐` : paneTitles[index]
}

/* RSI 과매수, 과매도 기준선. 값이 아니라 읽는 기준이다. */
const strengthGuides = [70, 30]

/* 체결 구간 앞뒤로 남길 최소 여백. 첫 체결과 마지막 체결이 화면 끝에 붙지 않게 한다. */
const dayInSeconds = 86_400

/* 토큰은 16진수라 투명도를 붙이려면 rgba 로 바꿔야 한다. */
function withAlpha(color: string, alpha: number): string {
  const hex = color.replace('#', '')
  if (hex.length !== 6) {
    return color
  }
  const value = Number.parseInt(hex, 16)
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`
}

/*
 * 봉 차트와 보조지표. 지표는 같은 창의 별도 패널에 둔다. 거래량과 RSI 는 가격과 축이 달라
 * 같은 패널에 그리면 봉이 눌린다.
 */
export function MinuteCandleChart({ candles, data, indicators, markers }: MinuteCandleChartProps) {
  const container = useRef<HTMLDivElement>(null)
  const chart = useRef<IChartApi>(null)
  const price = useRef<ISeriesApi<'Candlestick'>>(null)
  const bandFill = useRef<ISeriesApi<'Area'>[]>([])
  const averages = useRef<ISeriesApi<'Line'>[]>([])
  const bands = useRef<ISeriesApi<'Line'>[]>([])
  const volume = useRef<ISeriesApi<'Histogram'>>(null)
  const strength = useRef<ISeriesApi<'Line'>>(null)
  const paneLabels = useRef<ITextWatermarkPluginApi<Time>[]>([])
  /* 꺼진 아래 패널. 라벨에 "꺼짐" 을 붙일 때와 테마가 바뀌어 라벨을 다시 그릴 때 함께 읽는다. */
  const paneOff = useRef<boolean[]>([false, false])
  const tradeMarkers = useRef<ISeriesMarkersPluginApi<Time>>(null)

  useEffect(() => {
    if (!container.current) return
    const created = createChart(container.current, {
      autoSize: true,
      /*
       * 지표를 켜고 꺼도 봉 자리가 같아야 한다. 축 폭은 라벨 길이에 따라 자동으로 바뀌므로 고정하고,
       * 오른쪽 끝을 최신 봉에 붙이며, 폭이 바뀌어도 같은 기간이 보이게 한다.
       */
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
        barSpacing,
        minBarSpacing: 0.5,
        rightOffset: 4,
        borderVisible: false,
        fixRightEdge: true,
        lockVisibleTimeRangeOnResize: true,
      },
      rightPriceScale: { borderVisible: false, minimumWidth: priceScaleWidth },
    })
    chart.current = created

    /*
     * 그리는 순서가 겹치는 순서다. 볼린저 띠는 봉 뒤에 깔려야 하므로 봉보다 먼저 만든다.
     * 위 밴드까지 채운 뒤 아래 밴드를 배경색으로 덮어 두 선 사이만 남긴다.
     */
    bandFill.current = [0, 1].map(() =>
      created.addSeries(AreaSeries, {
        lineColor: 'transparent',
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
      }),
    )

    price.current = created.addSeries(CandlestickSeries, { borderVisible: false })
    tradeMarkers.current = createSeriesMarkers(price.current, [])
    averages.current = movingAveragePeriods.map(() => created.addSeries(LineSeries, { lineWidth: 1, priceLineVisible: false, lastValueVisible: false }))
    bands.current = [0, 1, 2].map(() => created.addSeries(LineSeries, { lineWidth: 1, priceLineVisible: false, lastValueVisible: false }))
    /* 패널 1 은 거래량, 패널 2 는 RSI 다. 순서를 바꾸면 축 눈금이 뒤섞인다. */
    volume.current = created.addSeries(HistogramSeries, { priceFormat: { type: 'volume' }, priceLineVisible: false }, 1)
    strength.current = created.addSeries(LineSeries, { lineWidth: 1, priceLineVisible: false }, 2)

    /* 아래 두 패널은 축이 달라 무엇을 그린 것인지 이름을 붙여야 읽힌다. 목업도 좌상단에 적는다. */
    const panes = created.panes()
    paneLabels.current = paneTitles
      .map((text, index) => [panes[index + 1], text] as const)
      .filter(([pane]) => pane !== undefined)
      .map(([pane]) => createTextWatermark(pane, { horzAlign: 'left', vertAlign: 'top' }))
    panes[1]?.setHeight(subPaneHeight)
    panes[2]?.setHeight(subPaneHeight)

    const applyTheme = () => {
      const style = getComputedStyle(document.documentElement)
      const token = (name: string) => style.getPropertyValue(name).trim()
      const panel = token('--panel')
      paneLabels.current.forEach((label, index) =>
        label.applyOptions({
          lines: [{ text: paneLabelText(index, paneOff.current[index] ?? false), color: token('--ink-3'), fontSize: 10, fontFamily: '"IBM Plex Mono", monospace' }],
        }),
      )
      created.applyOptions({
        layout: { background: { type: ColorType.Solid, color: panel }, textColor: token('--ink-2'), attributionLogo: true },
        /* 목업은 가로선만 그린다. 세로선은 봉을 가린다. */
        grid: { vertLines: { visible: false }, horzLines: { color: token('--line') } },
      })
      price.current?.applyOptions({ upColor: token('--up'), downColor: token('--down'), wickUpColor: token('--up'), wickDownColor: token('--down') })
      bandFill.current[0]?.applyOptions({ topColor: withAlpha(token('--bb'), 0.16), bottomColor: withAlpha(token('--bb'), 0.16) })
      bandFill.current[1]?.applyOptions({ topColor: panel, bottomColor: panel })
      averages.current.forEach((series, index) => series.applyOptions({ color: token(`--ma${movingAveragePeriods[index]}`) }))
      bands.current.forEach((series) => series.applyOptions({ color: token('--bb') }))
      strength.current?.applyOptions({ color: token('--accent') })
    }
    applyTheme()
    const observer = new MutationObserver(applyTheme)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })

    for (const guide of strengthGuides) {
      strength.current?.createPriceLine({ price: guide, color: 'rgba(128,128,128,0.45)', lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true, title: '' })
    }

    return () => {
      observer.disconnect()
      for (const label of paneLabels.current) label.detach()
      paneLabels.current = []
      created.remove()
      chart.current = null
      price.current = null
      volume.current = null
      strength.current = null
      averages.current = []
      bands.current = []
      bandFill.current = []
      tradeMarkers.current = null
    }
  }, [])


  useEffect(() => {
    price.current?.setData(
      candles.map((candle) => ({ time: toTime(candle.time), open: candle.open, high: candle.high, low: candle.low, close: candle.close })),
    )
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
    bandFill.current[0]?.setData(showBb ? line(data.bands.upper) : [])
    bandFill.current[1]?.setData(showBb ? line(data.bands.lower) : [])
    volume.current?.setData(
      showVol
        ? data.volume.map((bar) => ({ time: toTime(bar.time), value: bar.value, color: withAlpha(token(bar.rising ? '--up' : '--down'), 0.55) }))
        : [],
    )
    strength.current?.setData(showRsi ? line(data.strength) : [])

    /* 꺼진 패널도 자리를 지킨다. 접으면 봉 패널 높이가 바뀌어 지표를 누를 때마다 봉이 다시 잡힌다. 라벨로만 알린다. */
    paneOff.current = [!showVol, !showRsi]
    paneLabels.current.forEach((label, index) =>
      label.applyOptions({
        lines: [{ text: paneLabelText(index, paneOff.current[index] ?? false), color: token('--ink-3'), fontSize: 10, fontFamily: '"IBM Plex Mono", monospace' }],
      }),
    )
  }, [data, indicators])

  useEffect(() => {
    const style = getComputedStyle(document.documentElement)
    const token = (name: string) => style.getPropertyValue(name).trim()
    /* 색 계약대로 매수는 상승색, 매도는 하락색이다. 화살표는 봉을 가리지 않게 바깥에 붙인다. */
    tradeMarkers.current?.setMarkers(
      (markers ?? []).map((marker) => ({
        time: toTime(marker.time),
        position: marker.side === 'BUY' ? ('belowBar' as const) : ('aboveBar' as const),
        color: token(marker.side === 'BUY' ? '--up' : '--down'),
        shape: marker.side === 'BUY' ? ('arrowUp' as const) : ('arrowDown' as const),
        text: marker.side === 'BUY' ? '매수' : '매도',
      })),
    )

    /*
     * 체결을 보여 주려고 그린 차트이므로 체결이 있는 구간을 첫 화면에 맞춘다.
     * 최근 구간만 보이면 지난 실행의 마커가 화면 밖에 있어 아무것도 없는 것처럼 보인다.
     */
    const first = markers?.at(0)
    const last = markers?.at(-1)
    const oldest = candles.at(0)
    const newest = candles.at(-1)
    /* 보관 중인 봉 밖의 구간은 맞출 수 없다. 그럴 때는 기본 위치를 그대로 둔다. */
    if (first !== undefined && last !== undefined && oldest !== undefined && newest !== undefined) {
      const pad = Math.max(dayInSeconds, (last.time - first.time) * 0.05)
      const from = Math.max(first.time - pad, oldest.time)
      const to = Math.min(last.time + pad, newest.time)
      if (from < to) {
        chart.current?.timeScale().setVisibleRange({ from: toTime(from), to: toTime(to) })
      }
    }
  }, [markers, candles])

  /* id 는 목업이 정한 차트 자리다. base.css 의 #view-chart #candle 이 크기를 맡는다. */
  return <div ref={container} id="candle" role="img" aria-label="선택 종목의 봉 차트" />
}
