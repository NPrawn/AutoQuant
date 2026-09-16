import { timeframeLabels, timeframes } from '../strategy/labels'
import { formatPercent } from './labels'
import type { ContestRoundDetailResponse } from './types'

/*
 * 목업 31 의 채점 산식 카드. Calmar 대신 ADR-0003 의 수익률·안정성 점수를 쓴다
 * (대회 기능 설계 9절). 숫자는 회차 응답이 준 값만 적고 화면이 규칙을 다시 정하지 않는다.
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
          <i>수익률 점수 =</i> 일반장 누적 수익률 백분위 <i>(참가자 내)</i>
        </div>
        <div>
          <i>안정성 점수 =</i> 상승·하락·횡보 낙폭 백분위의 평균 × 0.6 + 최저 × 0.4
        </div>
        <div className="eq">
          <i>종합 =</i> 수익률 점수 × <b>{rules.returnWeight}</b> + 안정성 점수 × <b>{rules.stabilityWeight}</b>
        </div>
      </div>
      <div className="krow" style={{ marginTop: 16 }}>
        <span>실격 — 구간 수익률</span>
        <span>{formatPercent(rules.minimumReturn, 0)} 미만</span>
      </div>
      <div className="krow">
        <span>실격 — 구간 최대 낙폭</span>
        <span>{formatPercent(rules.maximumDrawdown, 0)} 초과</span>
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
            <span>{timeframeLabels[timeframe]}</span>
            <span className="mono">{round.factorPeriodCaps[timeframe]}봉 이하</span>
          </div>
        ))}
      <p className="sec-sub" style={{ marginTop: 12 }}>
        상한을 넘는 팩터가 하나라도 있으면 제출이 거절됩니다. 전략을 고친 뒤 다시 제출해 주세요.
      </p>
    </div>
  )
}
