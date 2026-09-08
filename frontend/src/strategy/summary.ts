import { describeCondition, describeRanking, logicLabels } from './labels'
import type { ConditionFilterNode, FilterNode, StrategyDsl } from './types'

/*
 * 저장된 DSL 을 목록·상세에서 짧게 읽어 주는 함수. 편집 상태를 거치지 않고 DSL 을 바로 읽는다.
 * 화면이 편집할 수 없는 형태의 조건은 개수로만 알린다.
 */
type SimpleConditionNode = ConditionFilterNode & {
  left: Extract<ConditionFilterNode['left'], { type: 'FACTOR' }>
  right: Extract<ConditionFilterNode['right'], { type: 'VALUE' }>
}

const isSimpleCondition = (node: FilterNode): node is SimpleConditionNode =>
  node.type === 'CONDITION' && node.left.type === 'FACTOR' && node.right.type === 'VALUE'

export type FilterSummary = {
  logic: string
  conditions: string[]
  otherCount: number
}

export function summarizeFilters(dsl: StrategyDsl): FilterSummary {
  const filters = dsl.filters
  const children = filters.type === 'GROUP' ? filters.children : [filters]
  const simple = children.filter(isSimpleCondition)
  return {
    logic: logicLabels[filters.type === 'GROUP' ? filters.logic : 'AND'],
    conditions: simple.map((node) => describeCondition(node.left.factor, node.operator, node.right.value)),
    otherCount: children.length - simple.length,
  }
}

/* 목록 한 줄용. "거래량 비율 > 1.20배 AND SMA 이격률 > 0.0% · 20기간 수익률 ↓" */
export function summarizeDsl(dsl: StrategyDsl): string {
  const { logic, conditions, otherCount } = summarizeFilters(dsl)
  const filterText = [
    ...(conditions.length > 0 ? [conditions.join(` ${logic} `)] : []),
    ...(otherCount > 0 ? [`기타 조건 ${otherCount}`] : []),
  ].join(' · ')
  return `${filterText || '조건 없음'} · ${describeRanking(dsl.ranking)}`
}
