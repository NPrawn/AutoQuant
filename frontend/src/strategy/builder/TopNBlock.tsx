import { topNOptions } from '../draft'

type TopNBlockProps = {
  topN: number
  onChange: (topN: number) => void
  /* 전략봇이 방금 이 값을 바꿨는지. 잠깐 강조하고 꺼진다. */
  changed?: boolean
}

/* 블록 03. 투자 종목 수. 순위 상위 N 종목에 동일 비중으로 나눠 담는다. 목업의 .topn-seg 를 쓴다. */
export function TopNBlock({ topN, onChange, changed = false }: TopNBlockProps) {
  const weight = topN > 0 ? (100 / topN).toFixed(1) : '—'
  return (
    <div className={changed ? 'blk pf changed' : 'blk pf'} data-cat="alloc">
      <div className="blk-title">
        <span className="blk-index">03</span>
        <div>
          <span className="bt">몇 종목에 나눠 담을까요?</span>
          <small>순위 상위 {topN}종목에 {weight}% 씩 동일 비중으로 담고, 순위에서 빠진 종목은 다음 재선정에 팝니다.</small>
        </div>
      </div>
      <div className="blk-body">
        <div className="topn-seg" role="radiogroup" aria-label="투자 종목 수">
          {topNOptions(topN).map((option) => (
            <button
              key={option}
              type="button"
              role="radio"
              aria-checked={option === topN}
              className={option === topN ? 'on' : undefined}
              onClick={() => onChange(option)}
            >
              Top {option}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
