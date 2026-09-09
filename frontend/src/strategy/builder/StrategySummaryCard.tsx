import type { DraftIssue, StrategyDraft } from '../draft'
import { describeCondition, describeRanking, logicLabels, rebalanceMeta } from '../labels'

type StrategySummaryCardProps = {
  draft: StrategyDraft
  goal: string | null
  issues: DraftIssue[]
  /* 전략봇이 방금 투자 종목 수를 바꿨는지. 그 값은 블록이 아니라 여기에만 보인다. */
  changed?: boolean
}

/* 서비스 고정 정책. 후보 유니버스와 매도 규칙은 화면에서 바꾸지 않는다. */
const universeSize = 30

/*
 * 우측 요약 카드. 블록의 값을 문장으로 다시 읽어 준다. 통과 종목 수는 시세 데이터가
 * 필요해 백테스트에서 계산되므로 여기서는 자리만 둔다.
 */
export function StrategySummaryCard({ draft, goal, issues, changed = false }: StrategySummaryCardProps) {
  const simpleConditions = draft.conditions.filter((condition) => condition.kind === 'simple')
  const rawCount = draft.conditions.length - simpleConditions.length
  const weight = draft.topN > 0 ? (100 / draft.topN).toFixed(1) : '—'

  return (
    <section className={changed ? 'card card--lead sum-card changed' : 'card card--lead sum-card'} aria-live="polite">
      <h3>이 전략 요약</h3>
      <p>{goal ? `"${goal}"` : '목표 문장 없이 직접 구성하는 전략'}</p>

      <div className="sum-flow">
        <strong>{universeSize}</strong>
        <i aria-hidden="true">→</i>
        <strong title="통과 종목 수는 백테스트에서 계산됩니다">—</strong>
        <i aria-hidden="true">→</i>
        <strong>{draft.topN}</strong>
        <span style={{ marginLeft: 'auto' }}>후보 → 통과 → 투자</span>
      </div>

      <div className="sum-row">
        <span>후보 조건</span>
        <b className="txt">
          {draft.conditions.length === 0
            ? '없음'
            : simpleConditions.map((condition, index) => (
                <span key={condition.id}>
                  {index > 0 && <span style={{ color: 'var(--ink-4)' }}> {logicLabels[draft.logic]} </span>}
                  {describeCondition(condition.factor, condition.operator, condition.value)}
                </span>
              ))}
          {rawCount > 0 && <span style={{ color: 'var(--ink-3)' }}> · 편집 불가 조건 {rawCount}</span>}
        </b>
      </div>
      <div className="sum-row">
        <span>순위 기준</span>
        <b className="txt">{describeRanking(draft.ranking)}</b>
      </div>
      <div className="sum-row">
        <span>재선정</span>
        <b className="txt">{rebalanceMeta[draft.rebalance].label}</b>
      </div>
      <div className="sum-row">
        <span>종목당 비중</span>
        <b className="num">{weight}%</b>
      </div>

      <div className="sum-fixed">
        고정 정책 · <b>업비트 KRW 거래대금 상위 {universeSize}</b>
        <br />
        <b>Top {draft.topN} 동일비중</b> · 매도는 <b>Top {draft.topN} 탈락</b>
      </div>

      {issues.length > 0 && (
        <div className="sum-warn" role="alert">
          <b>저장 전에 확인하세요.</b> {issues.map((issue) => issue.message).join(' ')}
        </div>
      )}
    </section>
  )
}
