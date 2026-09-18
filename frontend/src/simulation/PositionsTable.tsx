import { blank, formatKrw, formatPriceKrw, formatQuantity, formatRate, trendClass } from '../backtest/labels'
import { assetCode, assetName, type AssetLookup } from './assetLookup'
import type { PositionValuation } from './positions'

type PositionsTableProps = {
  valuations: PositionValuation[]
  lookup: AssetLookup
  /* 아래 차트가 보고 있는 종목. 고를 수 없는 화면에서는 null 이다. */
  selectedId?: string | null
  onSelect?: (assetId: string) => void
}

/* 보유 종목 표. 현재가가 없는 종목은 평가액과 수익률을 비운다. 반쯤 맞는 숫자를 보이지 않는다. */
export function PositionsTable({ valuations, lookup, selectedId = null, onSelect }: PositionsTableProps) {
  if (valuations.length === 0) {
    return <p className="goal-note" style={{ margin: 0 }}>담은 종목이 없어 현금으로 대기 중입니다. 다음 재선정에서 후보 조건을 통과한 종목을 담습니다.</p>
  }

  return (
    <div className="table-scroll" tabIndex={0} role="region" aria-label="보유 종목 표">
      <table>
        <thead>
          <tr>
            <th scope="col">종목</th>
            <th scope="col" className="r">수량</th>
            <th scope="col" className="r">평균 매수가</th>
            <th scope="col" className="r">현재가</th>
            <th scope="col" className="r">평가액</th>
            <th scope="col" className="r">수익률</th>
          </tr>
        </thead>
        <tbody>
          {valuations.map((valuation) => {
            const name = assetName(lookup, valuation.assetId)
            const selectable = onSelect !== undefined
            const selected = selectable && valuation.assetId === selectedId
            return (
              <tr
                key={valuation.assetId}
                className={selectable ? (selected ? 'click on' : 'click') : undefined}
                aria-current={selected ? 'true' : undefined}
                onClick={selectable ? () => onSelect(valuation.assetId) : undefined}
              >
                <td>
                  {/* 행 전체가 클릭 영역이지만 키보드 사용자는 이 버튼으로 고른다. */}
                  {selectable ? (
                    <button className="rank-action" type="button" onClick={() => onSelect(valuation.assetId)}>
                      {assetCode(lookup, valuation.assetId)}
                    </button>
                  ) : (
                    <b>{assetCode(lookup, valuation.assetId)}</b>
                  )}
                  {name !== null && <small className="muted-cell" style={{ marginLeft: 6 }}>{name}</small>}
                </td>
                <td className="r mono">{formatQuantity(valuation.quantity)}</td>
                <td className="r mono">{formatPriceKrw(valuation.averageEntryPrice)}</td>
                <td className="r mono">{valuation.price === null ? blank : formatPriceKrw(valuation.price)}</td>
                <td className="r mono">{valuation.value === null ? blank : formatKrw(valuation.value)}</td>
                <td className={`r mono ${trendClass(valuation.pnlRate)}`}>{formatRate(valuation.pnlRate, { signed: true })}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
