import { factorMeta } from './labels'
import {
  strategyNameMaxLength,
  type ComparisonOperator,
  type FactorType,
  type FilterNode,
  type Logic,
  type Ranking,
  type RebalancePeriod,
  type StrategyDsl,
  type Timeframe,
} from './types'

/*
 * 빌더가 편집하는 상태. DSL 은 조건에 식별자가 없어 React 목록 키와 삭제 대상을 정할 수
 * 없으므로 조건마다 id 를 붙인 별도 모양을 둔다. 화면이 편집할 수 있는 조건은 "지표 (기간)
 * 비교연산자 값" 형태 하나다. AI 가 그 밖의 형태(중첩 그룹, 지표 대 지표 비교)를 보내면
 * 원본 노드를 그대로 보존해 저장할 때 잃지 않게 한다.
 */
export type SimpleCondition = {
  id: string
  kind: 'simple'
  factor: FactorType
  period: number
  operator: ComparisonOperator
  value: number
}

export type RawCondition = {
  id: string
  kind: 'raw'
  node: FilterNode
}

export type ConditionDraft = SimpleCondition | RawCondition

export type StrategyDraft = {
  name: string
  description: string
  timeframe: Timeframe
  logic: Logic
  conditions: ConditionDraft[]
  ranking: Ranking
  topN: number
  rebalance: RebalancePeriod
}

/* 목업의 서비스 정책. 후보 조건은 4개까지, 투자 종목은 Top 5 동일비중이다. */
export const conditionLimit = 4
export const defaultTopN = 5

const newId = (): string => crypto.randomUUID()

export function createEmptyDraft(): StrategyDraft {
  return {
    name: '',
    description: '',
    timeframe: '1D',
    logic: 'AND',
    conditions: [],
    ranking: { factor: 'RETURN', period: factorMeta.RETURN.defaultPeriod, direction: 'DESC' },
    topN: defaultTopN,
    rebalance: 'WEEKLY',
  }
}

export function createCondition(factor: FactorType = 'RSI'): SimpleCondition {
  return {
    id: newId(),
    kind: 'simple',
    factor,
    period: factorMeta[factor].defaultPeriod,
    operator: 'LT',
    value: factor === 'RSI' ? 70 : 0,
  }
}

/* --- DSL ↔ 편집 상태 --- */

function conditionFromNode(node: FilterNode): ConditionDraft {
  if (node.type === 'CONDITION' && node.left.type === 'FACTOR' && node.right.type === 'VALUE') {
    return {
      id: newId(),
      kind: 'simple',
      factor: node.left.factor,
      period: node.left.period,
      operator: node.operator,
      value: node.right.value,
    }
  }
  return { id: newId(), kind: 'raw', node }
}

export function draftFromDsl(dsl: StrategyDsl, base?: Pick<StrategyDraft, 'name' | 'description'>): StrategyDraft {
  const filters = dsl.filters
  const logic: Logic = filters.type === 'GROUP' ? filters.logic : 'AND'
  const children = filters.type === 'GROUP' ? filters.children : [filters]

  return {
    name: base?.name ?? '',
    description: base?.description ?? '',
    timeframe: dsl.timeframe,
    logic,
    conditions: children.map(conditionFromNode),
    ranking: { ...dsl.ranking },
    topN: dsl.selection.topN,
    rebalance: dsl.rebalance.period,
  }
}

function nodeFromCondition(condition: ConditionDraft): FilterNode {
  if (condition.kind === 'raw') {
    return condition.node
  }
  return {
    type: 'CONDITION',
    left: { type: 'FACTOR', factor: condition.factor, period: condition.period },
    operator: condition.operator,
    right: { type: 'VALUE', value: condition.value },
  }
}

/* 백엔드는 GROUP 의 children 이 비어 있으면 거부한다. 저장 전에 validateDraft 로 걸러야 한다. */
export function dslFromDraft(draft: StrategyDraft): StrategyDsl {
  return {
    timeframe: draft.timeframe,
    filters: { type: 'GROUP', logic: draft.logic, children: draft.conditions.map(nodeFromCondition) },
    ranking: { ...draft.ranking },
    selection: { topN: draft.topN },
    rebalance: { period: draft.rebalance },
  }
}

/* --- 편집 동작. 모두 새 객체를 돌려주고 인수를 바꾸지 않는다. --- */

export function addCondition(draft: StrategyDraft): StrategyDraft {
  if (draft.conditions.length >= conditionLimit) {
    return draft
  }
  return { ...draft, conditions: [...draft.conditions, createCondition()] }
}

export function removeCondition(draft: StrategyDraft, id: string): StrategyDraft {
  return { ...draft, conditions: draft.conditions.filter((condition) => condition.id !== id) }
}

export type SimpleConditionPatch = Partial<Pick<SimpleCondition, 'factor' | 'period' | 'operator' | 'value'>>

/* 지표를 바꾸면 기간은 그 지표의 기본값으로 돌아간다. RSI 14 를 수익률 14 로 두는 실수를 막는다. */
export function updateCondition(draft: StrategyDraft, id: string, patch: SimpleConditionPatch): StrategyDraft {
  return {
    ...draft,
    conditions: draft.conditions.map((condition) => {
      if (condition.id !== id || condition.kind !== 'simple') {
        return condition
      }
      const nextFactor = patch.factor ?? condition.factor
      const factorChanged = nextFactor !== condition.factor
      return {
        ...condition,
        ...patch,
        period: patch.period ?? (factorChanged ? factorMeta[nextFactor].defaultPeriod : condition.period),
      }
    }),
  }
}

export function setLogic(draft: StrategyDraft, logic: Logic): StrategyDraft {
  return { ...draft, logic }
}

export function setRanking(draft: StrategyDraft, patch: Partial<Ranking>): StrategyDraft {
  return { ...draft, ranking: { ...draft.ranking, ...patch } }
}

export function setTopN(draft: StrategyDraft, topN: number): StrategyDraft {
  return { ...draft, topN }
}

export function setRebalance(draft: StrategyDraft, rebalance: RebalancePeriod): StrategyDraft {
  return { ...draft, rebalance }
}

export function setTimeframe(draft: StrategyDraft, timeframe: Timeframe): StrategyDraft {
  return { ...draft, timeframe }
}

export function setName(draft: StrategyDraft, name: string): StrategyDraft {
  return { ...draft, name }
}

export function setDescription(draft: StrategyDraft, description: string): StrategyDraft {
  return { ...draft, description }
}

/* --- 저장 전 검증. 백엔드 Bean Validation 과 같은 규칙을 화면에서 먼저 알린다. --- */

export type DraftIssue = {
  field: 'name' | 'conditions' | `condition.${string}` | 'ranking' | 'topN'
  message: string
}

const isPositiveInteger = (value: number): boolean => Number.isInteger(value) && value > 0

export function validateDraft(draft: StrategyDraft): DraftIssue[] {
  const issues: DraftIssue[] = []

  if (draft.name.trim() === '') {
    issues.push({ field: 'name', message: '전략 이름을 입력해 주세요.' })
  } else if (draft.name.length > strategyNameMaxLength) {
    issues.push({ field: 'name', message: `전략 이름은 ${strategyNameMaxLength}자 이하여야 합니다.` })
  }

  if (draft.conditions.length === 0) {
    issues.push({ field: 'conditions', message: '후보 조건을 하나 이상 넣어 주세요.' })
  }
  for (const condition of draft.conditions) {
    if (condition.kind !== 'simple') {
      continue
    }
    if (!isPositiveInteger(condition.period)) {
      issues.push({ field: `condition.${condition.id}`, message: '기간은 1 이상의 정수여야 합니다.' })
    }
    if (!Number.isFinite(condition.value)) {
      issues.push({ field: `condition.${condition.id}`, message: '기준값을 숫자로 입력해 주세요.' })
    }
  }

  if (!isPositiveInteger(draft.ranking.period)) {
    issues.push({ field: 'ranking', message: '순위 기간은 1 이상의 정수여야 합니다.' })
  }
  if (!isPositiveInteger(draft.topN)) {
    issues.push({ field: 'topN', message: '투자 종목 수는 1 이상이어야 합니다.' })
  }

  return issues
}
