import { LoadingState } from '../components/LoadingState'
import { Pager } from '../components/Pager'
import { EmptyState, ErrorState } from '../components/StateBlock'
import { formatDateTime, formatKrw, formatQuantity, sideLabels, formatPriceKrw } from './labels'
import { tradePageSize, useBacktestTrades } from './queries'

type TradeTableProps = {
  backtestId: string
  page: number
  onPageChange: (page: number) => void
}

/* 거래 내역. 목업 12 에는 없지만 체결 조회 API 가 있고 검증에 필요해 결과 아래에 둔다. */
export function TradeTable({ backtestId, page, onPageChange }: TradeTableProps) {
  const trades = useBacktestTrades(backtestId, page, true)

  if (trades.isPending) {
    return <LoadingState label="거래 내역을 불러오는 중…" />
  }
  if (trades.isError) {
    return <ErrorState title="거래 내역을 불러오지 못했습니다" error={trades.error} onRetry={() => trades.refetch()} />
  }
  if (trades.data.totalElements === 0) {
    return <EmptyState title="체결된 거래가 없습니다" description="조건을 통과한 종목이 없어 매수가 일어나지 않았습니다." />
  }

  const { content, totalElements, totalPages } = trades.data
  const firstRowNumber = page * tradePageSize + 1

  return (
    <>
      <div className="sec-head">
        <div>
          <div className="sec-title">거래 내역</div>
          <div className="sec-sub">
            전체 {formatKrw(totalElements)}건 중 {firstRowNumber}–{firstRowNumber + content.length - 1}
          </div>
        </div>
      </div>

      <div className="card">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>체결 시각</th>
                <th>종목</th>
                <th>구분</th>
                <th className="r">체결가</th>
                <th className="r">수량</th>
                <th className="r">거래대금</th>
                <th className="r">수수료</th>
              </tr>
            </thead>
            <tbody>
              {content.map((trade) => (
                <tr key={trade.id}>
                  <td className="muted-cell">{formatDateTime(trade.filledAt)}</td>
                  <td>
                    <b>{trade.marketCode}</b>
                  </td>
                  <td>
                    <span className={trade.side === 'BUY' ? 'updown up' : 'updown down'}>{sideLabels[trade.side]}</span>
                  </td>
                  <td className="r mono">{formatPriceKrw(trade.executedPrice)}</td>
                  <td className="r mono">{formatQuantity(trade.quantity)}</td>
                  <td className="r mono">{formatKrw(trade.notional)}</td>
                  <td className="r mono muted-cell">{formatKrw(trade.feeAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <Pager page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </>
  )
}
