import { formatDateTime, formatKrw, formatPriceKrw, formatQuantity, sideLabels } from '../backtest/labels'
import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { ErrorState } from '../components/StateBlock'
import { assetCode, type AssetLookup } from './assetLookup'
import { tradePageSize, useSimulationTrades } from './queries'

type SimulationTradeTableProps = {
  simulationId: string
  live: boolean
  lookup: AssetLookup
  page: number
  onPageChange: (page: number) => void
}

/* 체결 원장(목업 18). 백테스트 거래 내역과 같은 열이고 종목은 식별자를 마켓 코드로 바꿔 보인다. */
export function SimulationTradeTable({ simulationId, live, lookup, page, onPageChange }: SimulationTradeTableProps) {
  const trades = useSimulationTrades(simulationId, page, live)

  if (trades.isPending) {
    return <LoadingState label="체결 기록을 불러오는 중…" />
  }
  if (trades.isError) {
    return <ErrorState title="체결 기록을 불러오지 못했습니다" error={trades.error} onRetry={() => trades.refetch()} />
  }
  if (trades.data.totalElements === 0) {
    return <p className="goal-note" style={{ margin: 0 }}>{live ? '체결된 거래가 없습니다. 재선정 결과에 따라 매수·매도가 여기에 기록됩니다.' : '체결된 거래가 없습니다.'}</p>
  }

  const { content, totalElements, totalPages } = trades.data
  const firstRowNumber = page * tradePageSize + 1

  return (
    <>
      <p className="live-note" style={{ margin: '0 0 10px' }}>
        전체 {formatKrw(totalElements)}건 중 {firstRowNumber}–{firstRowNumber + content.length - 1}
      </p>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="체결 원장 표">
        <table>
          <thead>
            <tr>
              <th scope="col">체결 시각</th>
              <th scope="col">구분</th>
              <th scope="col">종목</th>
              <th scope="col">근거</th>
              <th scope="col" className="r">수량</th>
              <th scope="col" className="r">체결가</th>
              <th scope="col" className="r">거래대금</th>
              <th scope="col" className="r">수수료</th>
            </tr>
          </thead>
          <tbody>
            {content.map((trade) => (
              <tr key={trade.id}>
                <td className="mono muted-cell">{formatDateTime(trade.filledAt)}</td>
                <td>
                  <span className={trade.side === 'BUY' ? 'chip good' : 'chip mute'}>{sideLabels[trade.side]}</span>
                </td>
                <td className="mono">{assetCode(lookup, trade.assetId)}</td>
                <td className="muted-cell">{trade.reason ?? '—'}</td>
                <td className="r mono">{formatQuantity(trade.quantity)}</td>
                <td className="r mono">{formatPriceKrw(trade.executedPrice)}</td>
                <td className="r mono">{formatKrw(trade.notional)}</td>
                <td className="r mono muted-cell">{formatKrw(trade.feeAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} disabled={trades.isPlaceholderData} onPageChange={onPageChange} />
    </>
  )
}
