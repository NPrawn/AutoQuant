import { ContestOutcomeGrid } from './ContestDatasetCards'
import { ContestMarketComparison } from './ContestMarketComparison'
import { describeDisqualification, formatScore, progressRatio } from './labels'
import { awaitingScore } from './types'
import type { ContestMyResultResponse } from './types'

type ContestMyScorePanelProps = {
  result: ContestMyResultResponse
  /* 마감 전에는 순위가 그때까지 채점된 제출 사이의 자리라 잠정이다. */
  provisional: boolean
}

/*
 * 내 제출 성적. 점수가 절대값이라 백테스트 4벌이 끝나면 바로 확정된다 (ADR-0002, ADR-0003).
 * 아직 도는 중이면 진행률만 보여 준다. 대회 상세와 결과 화면이 함께 쓴다.
 */
export function ContestMyScorePanel({ result, provisional }: ContestMyScorePanelProps) {
  if (awaitingScore(result)) {
    return <ScoringPanel result={result} />
  }

  const disqualification = describeDisqualification(result.disqualificationReason, result.disqualifiedKind)

  return (
    <>
      <section className="card score-hero card--lead">
        <div className="score-num">
          <span>종합 점수</span>
          {disqualification === null ? (
            <strong>{formatScore(result.totalScore)}</strong>
          ) : (
            <strong className="txt" style={{ color: 'var(--crit)' }}>
              제외
            </strong>
          )}
          <small>
            {disqualification === null
              ? `${result.entryCount}건 중 ${result.ranking ?? '—'}위${provisional ? ' (잠정)' : ''}`
              : '순위 산정에서 빠짐'}
          </small>
        </div>
        <div className="vr" />
        <div className="score-meta">
          <div>
            <span>수익률 점수</span>
            <strong>{formatScore(result.returnScore)}</strong>
            <em>일반장 시장 대비</em>
          </div>
          <div>
            <span>안정성 점수</span>
            <strong>{formatScore(result.stabilityScore)}</strong>
            <em>장세 3벌 낙폭</em>
          </div>
          <div>
            <span>1위 총점</span>
            <strong>{formatScore(result.topTotalScore)}</strong>
            <em>{provisional ? '지금까지 채점된 제출 중' : '같은 회차'}</em>
          </div>
        </div>
      </section>

      {disqualification !== null && (
        <div className="callout warn" style={{ marginTop: 20 }}>
          <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 9v4M12 17h.01" />
            <circle cx="12" cy="12" r="9" />
          </svg>
          <span>
            <b>{disqualification}</b> 으로 채점되지 않았습니다. 전략을 고쳐 다시 제출할 수 있습니다.
          </span>
        </div>
      )}

      <div className="sec-head" style={{ margin: '24px 0 12px' }}>
        <div>
          <div className="sec-title">장세별 성적</div>
          <div className="sec-sub">
            채점용 데이터 · 모든 참가자와 같은 조건{provisional && ' · 장세별 자산 곡선은 마감 후 공개됩니다'}
          </div>
        </div>
        <span className="chip mute">채점용</span>
      </div>
      <ContestOutcomeGrid outcomes={result.datasets} />
      <ContestMarketComparison outcomes={result.datasets} />

      <div className="card pad" style={{ marginTop: 16 }}>
        <div className="sec-head">
          <div>
            <div className="sec-title">국면 점수</div>
          </div>
        </div>
        <ScoreBar label="상승장" tone="bull" value={result.bullRegimeScore} />
        <ScoreBar label="하락장" tone="bear" value={result.bearRegimeScore} />
        <ScoreBar label="횡보장" tone="side" value={result.sidewaysRegimeScore} />
      </div>
    </>
  )
}

/* 제출 직후. 백테스트 4벌이 끝나야 점수가 나온다. */
function ScoringPanel({ result }: { result: ContestMyResultResponse }) {
  const ratio = progressRatio(result.finishedBacktests, result.totalBacktests)
  const percent = Math.round(ratio * 100)

  return (
    <div className="card pad">
      <div className="sec-head">
        <div>
          <div className="sec-title">내 제출 채점 중</div>
          <div className="sec-sub">{result.strategyName}을 일반·상승·하락·횡보 4벌에서 돌리고 있습니다</div>
        </div>
        <span className="chip mute">{percent}%</span>
      </div>
      <div className="pct">
        <span>백테스트</span>
        <div className="pct-track">
          <i style={{ width: `${percent}%` }} />
        </div>
        <span className="pct-val">
          {result.finishedBacktests} / {result.totalBacktests}
        </span>
      </div>
    </div>
  )
}

function ScoreBar({ label, tone, value }: { label: string; tone: 'bull' | 'bear' | 'side'; value: number | null }) {
  const width = value === null ? 0 : Math.min(Math.max(value, 0), 100)
  return (
    <div className={`pct ${tone}`}>
      <span>{label}</span>
      <div className="pct-track">
        <i style={{ width: `${width}%` }} />
      </div>
      <span className="pct-val">{value === null ? '—' : value.toFixed(0)}</span>
    </div>
  )
}
