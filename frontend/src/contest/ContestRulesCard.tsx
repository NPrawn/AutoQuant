import { timeframeLabel, timeframes } from '../strategy/labels'
import { formatPercent } from './labels'
import type { ContestRoundDetailResponse } from './types'

/*
 * 목업 31 의 채점 산식 카드. 점수는 남들과 견주지 않는 절대값이라 제출이 채점되는 대로 확정된다
 * (ADR-0002, ADR-0003). 숫자는 회차 응답이 준 값만 적고 화면이 규칙을 다시 정하지 않는다.
 */
export function ContestRulesCard({ round }: { round: ContestRoundDetailResponse }) {
  const rules = round.scoringRules

  return (
    <div className="card pad">
      <div className="sec-head">
        <div>
          <div className="sec-title">채점 산식</div>
          <div className="sec-sub">이번 회차 기준 · 회차마다 명시됩니다</div>
        </div>
      </div>
      <div className="formula">
        <div>
          <i>수익률 점수 =</i> 일반장에서 시장보다 얼마나 더 벌었는지 <i>(시장과 같으면 50점)</i>
        </div>
        <div>
          <i>국면 점수 =</i> 구간 낙폭을 그 장세의 시장 낙폭과 견준 값 <i>(시장만큼 빠지면 50점)</i>
        </div>
        <div>
          <i>안정성 점수 =</i> 상승·하락·횡보 국면 점수의 평균 × 0.6 + 최저 × 0.4
        </div>
        <div className="eq">
          <i>종합 =</i> 수익률 점수 × <b>{rules.returnWeight}</b> + 안정성 점수 × <b>{rules.stabilityWeight}</b>
        </div>
      </div>
      <div className="krow" style={{ marginTop: 16 }}>
        <span>실격 — 백테스트</span>
        <span>4벌 중 하나라도 실패</span>
      </div>
      <div className="krow">
        <span>실격 — 일반장 평균 투자 비중</span>
        <span>{formatPercent(rules.minimumExposure, 0)} 미만</span>
      </div>
      <div className="krow">
        <span>비용 · 체결</span>
        <span>
          수수료 {formatPercent(rules.feeRate, 2)} · 슬리피지 {rules.slippageBps}bp · 다음 봉 시가
        </span>
      </div>
      <div className="krow">
        <span>초기 자본</span>
        <span className="mono">{rules.initialCapital.toLocaleString('ko-KR')} KRW</span>
      </div>
    </div>
  )
}

/*
 * 팩터 기간 상한. 대회 데이터는 워밍업 구간이 짧아 그보다 긴 기간은 값을 만들 수 없다.
 * 넘으면 제출이 CONTEST-400-001 로 막히므로 제출 전에 숫자를 보여 준다.
 */
export function ContestFactorCapsCard({ round }: { round: ContestRoundDetailResponse }) {
  return (
    <div className="card pad">
      <div className="sec-head">
        <div>
          <div className="sec-title">팩터 기간 상한</div>
          <div className="sec-sub">
            워밍업 {round.warmupWeeks}주 · 평가 {round.evaluationWeeks}주 기준
          </div>
        </div>
      </div>
      {/* 응답의 Map 순서는 보장되지 않아 화면이 짧은 봉부터 세운다. */}
      {timeframes
        .filter((timeframe) => round.factorPeriodCaps[timeframe] !== undefined)
        .map((timeframe) => (
          <div className="krow" key={timeframe}>
            <span>{timeframeLabel(timeframe)}</span>
            <span className="mono">{round.factorPeriodCaps[timeframe]}봉 이하</span>
          </div>
        ))}
    </div>
  )
}
