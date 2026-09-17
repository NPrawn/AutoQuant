import { axisPercent, barSpan, comparisonAxis } from './charts'
import { datasetKindLabels, formatPercent } from './labels'
import type { ContestDatasetOutcomeResponse, MarketDatasetKind } from './types'

type Row = {
  kind: MarketDatasetKind
  mine: number | null
  market: number
}

/* 목업 .pct 의 장세 색. 일반장은 색이 없다. */
const toneClass: Record<MarketDatasetKind, string> = {
  GENERAL: 'pct',
  BULL: 'pct bull',
  BEAR: 'pct bear',
  SIDEWAYS: 'pct side',
}

/*
 * 목업 33 "상위권과 비교" 카드의 막대와 세로선으로 내 성적을 시장과 견준다.
 * 이미 공개된 수익률·낙폭만 쓰므로 마감 전에도 보인다.
 */
export function ContestMarketComparison({ outcomes }: { outcomes: ContestDatasetOutcomeResponse[] }) {
  return (
    <div className="two" style={{ marginTop: 16 }}>
      {/* 일반장 수익률이 수백 %라 한 축에 담으면 다른 장세 막대가 보이지 않는다. 행마다 축을 잡는다. */}
      <ComparisonCard
        title="시장 대비 수익률"
        sub="막대가 세로선보다 오른쪽이면 시장보다 더 벌었습니다"
        axisPerRow
        rows={outcomes.map((outcome) => ({
          kind: outcome.kind,
          mine: outcome.cumulativeReturn,
          market: outcome.marketReturn,
        }))}
      />
      <ComparisonCard
        title="시장 대비 낙폭"
        sub="막대가 세로선보다 짧으면 시장보다 덜 빠졌습니다"
        rows={outcomes.map((outcome) => ({
          kind: outcome.kind,
          mine: outcome.maximumDrawdown,
          market: outcome.marketMaximumDrawdown,
        }))}
      />
    </div>
  )
}

type ComparisonCardProps = {
  title: string
  sub: string
  rows: Row[]
  axisPerRow?: boolean
}

function ComparisonCard({ title, sub, rows, axisPerRow = false }: ComparisonCardProps) {
  const sharedAxis = comparisonAxis(rows.flatMap((row) => [row.mine, row.market]))

  return (
    <div className="card pad">
      <div className="sec-head">
        <div>
          <div className="sec-title">{title}</div>
          <div className="sec-sub">{sub}</div>
        </div>
      </div>
      {rows.map((row) => {
        const axis = axisPerRow ? comparisonAxis([row.mine, row.market]) : sharedAxis
        const span = row.mine === null ? null : barSpan(row.mine, axis)
        return (
          <div className={toneClass[row.kind]} key={row.kind}>
            <span>{datasetKindLabels[row.kind]}</span>
            <div
              className="pct-track"
              role="img"
              aria-label={`내 값 ${formatPercent(row.mine)}, 시장 ${formatPercent(row.market)}`}
            >
              {span !== null && <i style={{ left: `${span.left}%`, width: `${span.width}%` }} />}
              <b style={{ left: `${axisPercent(row.market, axis)}%` }} />
            </div>
            <span className="pct-val">{formatPercent(row.mine)}</span>
          </div>
        )
      })}
      <div className="perf-foot" style={{ marginTop: 16 }}>
        <span>
          막대가 내 값, 세로선이 시장 값입니다. 막대는 <b>0</b>에서 출발합니다.
          {axisPerRow && ' 행마다 축이 달라 행끼리 길이를 견주지 않습니다.'}
        </span>
        {!axisPerRow && (
          <span className="mono">
            {formatPercent(sharedAxis.low, 0)} ~ {formatPercent(sharedAxis.high, 0)}
          </span>
        )}
      </div>
    </div>
  )
}
