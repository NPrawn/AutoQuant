import { rebalanceMeta, rebalancePeriods } from '../labels'
import type { RebalancePeriod } from '../types'

type RebalanceBlockProps = {
  rebalance: RebalancePeriod
  onChange: (rebalance: RebalancePeriod) => void
}

/* 블록 03. 재선정 주기. 세그먼트 버튼은 라디오 그룹으로 읽히게 한다. */
export function RebalanceBlock({ rebalance, onChange }: RebalanceBlockProps) {
  return (
    <div className="blk ex" data-cat="exec" style={{ borderBottom: 0 }}>
      <div className="blk-title">
        <span className="blk-index">03</span>
        <div>
          <span className="bt">언제 다시 종목을 선정할까요?</span>
          <small>{rebalanceMeta[rebalance].note}</small>
        </div>
      </div>
      <div className="blk-body">
        <div className="rebal-seg" role="radiogroup" aria-label="재선정 주기">
          {rebalancePeriods.map((period) => (
            <button
              key={period}
              type="button"
              role="radio"
              aria-checked={period === rebalance}
              className={period === rebalance ? 'on' : undefined}
              onClick={() => onChange(period)}
            >
              {rebalanceMeta[period].label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
