import type { CandleResponse } from './types'

/*
 * 종목 목록과 현재가 영역이 쓰는 표시값. 가격은 API 가 문자열로 주고 정밀도를 잃으면 안 되므로
 * 표기는 문자열로 다듬고, 등락률처럼 비교가 필요한 값만 숫자로 바꾼다.
 */
export type Ticker = {
  /* 전일 종가. 등락의 기준이다. 일봉이 하나뿐이면 비교할 대상이 없어 null 이다. */
  previousClose: number | null
  /* 마지막 일봉의 종가. 체결이 아직 안 왔을 때 현재가 대신 쓴다. */
  lastClose: string | null
  /* 마지막 일봉의 거래대금. 종목 사이 순위를 매기는 데 쓴다. */
  tradeAmount: number | null
}

export function tickerFromDailyCandles(candles: readonly CandleResponse[]): Ticker {
  const last = candles.at(-1)
  const previous = candles.length >= 2 ? candles[candles.length - 2] : null
  return {
    previousClose: previous === null ? null : Number(previous.closePrice),
    lastClose: last?.closePrice ?? null,
    tradeAmount: last === undefined ? null : Number(last.tradeAmount),
  }
}

/* 기준가가 없거나 0 이면 등락을 계산하지 않는다. 0 으로 나눈 값을 화면에 내보내지 않는다. */
export function changeRate(price: string | null, previousClose: number | null): number | null {
  if (price === null || previousClose === null || previousClose === 0) {
    return null
  }
  return Number(price) / previousClose - 1
}

export type DayStats = {
  high: string
  low: string
  volume: number
}

/* 최근 24개의 1시간봉으로 하루 고가·저가·거래량을 만든다. 봉이 없으면 null 이다. */
export function summarizeDay(candles: readonly CandleResponse[]): DayStats | null {
  if (candles.length === 0) {
    return null
  }
  let high = candles[0].highPrice
  let low = candles[0].lowPrice
  let volume = 0
  for (const candle of candles) {
    if (Number(candle.highPrice) > Number(high)) high = candle.highPrice
    if (Number(candle.lowPrice) < Number(low)) low = candle.lowPrice
    volume += Number(candle.tradeVolume)
  }
  return { high, low, volume }
}

/*
 * 가격 표기. 숫자로 바꾸면 8자리 소수에서 정밀도를 잃을 수 있어 문자열을 그대로 다듬는다.
 * 의미 없는 뒤쪽 0 을 떼고 정수부에 자리 구분을 넣는다.
 */
export function formatPrice(raw: string | null): string {
  if (raw === null) {
    return '—'
  }
  const [whole, fraction = ''] = raw.split('.')
  const trimmed = fraction.replace(/0+$/, '')
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return trimmed === '' ? grouped : `${grouped}.${trimmed}`
}

export function formatChangeRate(rate: number | null): string {
  if (rate === null) {
    return '—'
  }
  const percent = rate * 100
  return `${percent > 0 ? '+' : ''}${percent.toFixed(2)}%`
}

/* 색 계약대로 빨강(--up)이 상승이다. 0 과 알 수 없는 값에는 색을 주지 않는다. */
export function changeClass(rate: number | null): string {
  if (rate === null || rate === 0) {
    return ''
  }
  return rate > 0 ? 'updown up' : 'updown down'
}

const volumeFormat = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 })

export function formatVolume(value: number): string {
  return volumeFormat.format(value)
}

/* 검색은 마켓 코드와 한글명 모두에서 찾는다. 대소문자는 구분하지 않는다. */
export function matchesQuery(asset: { marketCode: string; name: string }, query: string): boolean {
  const trimmed = query.trim().toLowerCase()
  if (trimmed === '') {
    return true
  }
  return asset.marketCode.toLowerCase().includes(trimmed) || asset.name.toLowerCase().includes(trimmed)
}

/* 목업은 마켓을 BTC/KRW 로 읽는다. 응답의 KRW-BTC 를 화면 표기로 바꾸는 곳은 여기뿐이다. */
export function marketSymbol(marketCode: string): string {
  return marketCode.split('-').at(-1) ?? marketCode
}

export function displayMarket(marketCode: string): string {
  const [quote, base] = marketCode.split('-')
  return base === undefined ? marketCode : `${base}/${quote}`
}
