import { blank, formatKrw, formatQuantity, formatRate, trendClass } from '../backtest/labels'
import { assetCode, assetName, type AssetLookup } from './assetLookup'
import type { PositionValuation } from './positions'

type PositionsTableProps = {
  valuations: PositionValuation[]
  lookup: AssetLookup
}

/* 보유 종목 표. 현재가가 없는 종목은 평가액과 수익률을 비운다. 반쯤 맞는 숫자를 보이지 않는다. */
export function PositionsTable({ valuations, lookup }: PositionsTableProps) {
  if (valuations.length === 0) {
    return <p className="goal-note" style={{ margin: 0 }}>담은 종목이 없어 현금으로 대기 중입니다. 다음 재선정에서 후보 조건을 통과한 종목을 담습니다.</p>
  }

  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>종목</th>
            <th className="r">수량</th>
            <th className="r">평균 매수가</th>
            <th className="r">현재가</th>
            <th className="r">평가액</th>
            <th className="r">수익률</th>
          </tr>
        </thead>
        <tbody>
          {valuations.map((valuation) => {
            const name = assetName(lookup, valuation.assetId)
            return (
              <tr key={valuation.assetId}>
                <td>
                  <b>{assetCode(lookup, valuation.assetId)}</b>
                  {name !== null && <small className="muted-cell" style={{ marginLeft: 6 }}>{name}</small>}
                </td>
                <td className="r mono">{formatQuantity(valuation.quantity)}</td>
                <td className="r mono">{formatKrw(valuation.averageEntryPrice)}</td>
                <td className="r mono">{valuation.price === null ? blank : formatKrw(valuation.price)}</td>
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
