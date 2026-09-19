import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/StateBlock'
import { timeframeLabel } from '../strategy/labels'
import type { Timeframe } from '../strategy/types'
import { factorRows, missingFactorNote } from './factors'
import type { TradeAmountRank } from './factors'
import { useAssetFactors } from './queries'

type FactorCardProps = {
  assetId: string
  timeframe: Timeframe
  /* 거래대금 순위는 전 종목을 견줘야 나오므로 목록을 들고 있는 화면이 계산해 넘긴다. */
  rank: TradeAmountRank | null
}

/* 차트 우측의 팩터 값 카드. 전략이 후보를 걸러내고 순위를 매길 때 쓰는 바로 그 숫자다. */
export function FactorCard({ assetId, timeframe, rank }: FactorCardProps) {
  const factors = useAssetFactors(assetId, timeframe)
  const rows = factorRows(factors.data)
  const note = missingFactorNote(factors.data)

  return (
    <section className="card pad">
      <div className="sec-title" style={{ marginBottom: 2 }}>팩터 값</div>
      <div className="sec-sub" style={{ marginBottom: 12 }}>
        {timeframeLabel(timeframe)} 기준, 전략이 이 숫자로 후보를 걸러내고 순위를 매깁니다
      </div>

      {factors.isPending ? (
        <LoadingState label="팩터를 계산하는 중…" />
      ) : factors.isError ? (
        <ErrorState title="팩터를 불러오지 못했습니다" error={factors.error} onRetry={() => void factors.refetch()} />
      ) : (
        <>
          {rows.map((row) => (
            <div className="krow" key={row.factor}>
              <span>{row.label}</span>
              <span className={row.tone}>{row.text}</span>
            </div>
          ))}
          {rank !== null && (
            <div className="krow">
              <span>거래대금 순위</span>
              <span>{rank.position} / {rank.total}</span>
            </div>
          )}
          {note !== null && (
            <p className="goal-note" style={{ margin: '12px 0 0' }}>{note}</p>
          )}
        </>
      )}
    </section>
  )
}
