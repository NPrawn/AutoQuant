import { useId } from 'react'

import { directionLabels, factorMeta, factorTypes, timeframeLabels, timeframes } from '../labels'
import type { FactorType, Ranking, RankingDirection, Timeframe } from '../types'

type RankingBlockProps = {
  ranking: Ranking
  timeframe: Timeframe
  onRankingChange: (patch: Partial<Ranking>) => void
  onTimeframeChange: (timeframe: Timeframe) => void
}

/* 블록 02. 순위 기준. 목업의 세 칸에 봉 주기를 더해 인라인 편집기의 4칸을 채운다. */
export function RankingBlock({ ranking, timeframe, onRankingChange, onTimeframeChange }: RankingBlockProps) {
  const id = useId()

  return (
    <div className="blk pf" data-cat="alloc">
      <div className="blk-title">
        <span className="blk-index">02</span>
        <div>
          <span className="bt">남은 코인의 순위를 어떻게 정할까요?</span>
        </div>
      </div>
      <div className="blk-body">
        <div className="inline-editor">
          <div className="edit-field">
            <label htmlFor={`${id}-factor`}>순위 기준</label>
            <select
              id={`${id}-factor`}
              value={ranking.factor}
              onChange={(event) => onRankingChange({ factor: event.target.value as FactorType })}
            >
              {factorTypes.map((factor) => (
                <option key={factor} value={factor}>
                  {factorMeta[factor].label}
                </option>
              ))}
            </select>
          </div>
          <div className="edit-field">
            <label htmlFor={`${id}-period`}>기간</label>
            <input
              id={`${id}-period`}
              className="num"
              type="number"
              min={1}
              max={500}
              step={1}
              value={Number.isNaN(ranking.period) ? '' : ranking.period}
              onChange={(event) => onRankingChange({ period: event.target.value.trim() === '' ? Number.NaN : Number(event.target.value) })}
            />
          </div>
          <div className="edit-field">
            <label htmlFor={`${id}-direction`}>정렬</label>
            <select
              id={`${id}-direction`}
              value={ranking.direction}
              onChange={(event) => onRankingChange({ direction: event.target.value as RankingDirection })}
            >
              {(Object.keys(directionLabels) as RankingDirection[]).map((direction) => (
                <option key={direction} value={direction}>
                  {directionLabels[direction]}
                </option>
              ))}
            </select>
          </div>
          <div className="edit-field">
            <label htmlFor={`${id}-timeframe`}>봉 주기</label>
            <select id={`${id}-timeframe`} value={timeframe} onChange={(event) => onTimeframeChange(event.target.value as Timeframe)}>
              {timeframes.map((value) => (
                <option key={value} value={value}>
                  {timeframeLabels[value]}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>
    </div>
  )
}
