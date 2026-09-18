import type { KeyboardEvent } from 'react'

import type { LivePrice } from './queries'
import { changeClass, changeRate, formatChangeRate, formatPrice, matchesQuery } from './ticker'
import type { Ticker } from './ticker'
import type { ChartAsset } from './types'

type CoinListProps = {
  assets: readonly ChartAsset[]
  tickers: ReadonlyMap<string, Ticker> | undefined
  prices: ReadonlyMap<string, LivePrice>
  selectedId: string
  query: string
  onQueryChange: (query: string) => void
  onSelect: (assetId: string) => void
}

/* 목업 04 의 좌측 패널. 수집 중인 종목을 검색하고 고른다. */
export function CoinList({ assets, tickers, prices, selectedId, query, onQueryChange, onSelect }: CoinListProps) {
  const shown = assets.filter((asset) => matchesQuery(asset, query))

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>, assetId: string) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onSelect(assetId)
    }
  }

  return (
    <>
      <div className="coin-search">
        <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
        <input type="text" value={query} placeholder="코인 검색" aria-label="코인 검색" onChange={(event) => onQueryChange(event.target.value)} />
      </div>
      <div className="cl-head">
        <span>수집 종목 {assets.length}</span>
        <span>현재가 / 변동</span>
      </div>
      <div className="coin-list" role="listbox" aria-label="종목 목록">
        {shown.length === 0 && <p className="goal-note" style={{ padding: '12px 10px', margin: 0 }}>검색 결과가 없습니다.</p>}
        {shown.map((asset) => {
          const ticker = tickers?.get(asset.id)
          const price = prices.get(asset.id)?.price ?? ticker?.lastClose ?? null
          const rate = changeRate(price, ticker?.previousClose ?? null)
          return (
            <div
              key={asset.id}
              className={asset.id === selectedId ? 'coin-row on' : 'coin-row'}
              role="option"
              aria-selected={asset.id === selectedId}
              tabIndex={0}
              onClick={() => onSelect(asset.id)}
              onKeyDown={(event) => handleKeyDown(event, asset.id)}
            >
              <div>
                <div className="sym">
                  {asset.marketCode.replace('KRW-', '')}
                  <span style={{ color: 'var(--ink-3)', fontWeight: 400 }}>/KRW</span>
                </div>
                <div className="kr">{asset.name}</div>
              </div>
              <div>
                <div className="px">{formatPrice(price)}</div>
                <div className={`pc ${changeClass(rate)}`}>{formatChangeRate(rate)}</div>
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}
