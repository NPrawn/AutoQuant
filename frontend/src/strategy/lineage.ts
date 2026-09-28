import type { Strategy } from './types.ts'

/*
 * 버전마다 행이 하나인 전략들을 한 전략으로 묶는다. 서버는 rootStrategyId 와 versionNo 만 주고 묶는 일은 화면이 한다.
 * 목록은 뿌리별 한 줄(최신 버전)이고, 상세는 같은 뿌리의 모든 버전으로 백테스트·모의투자 기록을 거른다.
 */
export type StrategyLineage = {
  rootId: string
  /* 가장 높은 버전. 목록의 대표다. */
  head: Strategy
  /* 버전 번호 내림차순. head 가 첫 번째다. */
  versions: Strategy[]
}

/* 서버 목록 순서를 따라 뿌리가 처음 나온 자리에 놓는다. */
export function groupByLineage(strategies: readonly Strategy[]): StrategyLineage[] {
  const byRoot = new Map<string, Strategy[]>()
  for (const strategy of strategies) {
    const versions = byRoot.get(strategy.rootId)
    if (versions === undefined) {
      byRoot.set(strategy.rootId, [strategy])
    } else {
      versions.push(strategy)
    }
  }
  return [...byRoot.entries()].map(([rootId, list]) => {
    const versions = [...list].sort((left, right) => right.version - left.version)
    return { rootId, head: versions[0], versions }
  })
}

/* 어느 버전을 보고 있든 그 전략 전체를 돌려준다. 목록에 없는 전략(삭제됨 등)이면 null 이다. */
export function lineageOf(strategies: readonly Strategy[], strategyId: string): StrategyLineage | null {
  const found = strategies.find((strategy) => strategy.id === strategyId)
  if (found === undefined) {
    return null
  }
  return groupByLineage(strategies.filter((strategy) => strategy.rootId === found.rootId))[0] ?? null
}

/* 기록을 거를 때 쓰는 버전 식별자 묶음. 전략 목록을 아직 못 받았으면 보고 있는 버전 하나만 쓴다. */
export function lineageIds(lineage: StrategyLineage | null, fallbackId: string): string[] {
  return lineage === null ? [fallbackId] : lineage.versions.map((version) => version.id)
}

export function versionOf(lineage: StrategyLineage | null, strategyId: string): number | null {
  return lineage?.versions.find((version) => version.id === strategyId)?.version ?? null
}

/* 백테스트·모의투자 목록 API 는 전략별 필터가 없어 화면이 거른다. 원래 순서를 유지한다. */
export function forVersions<T extends { strategyId: string }>(items: readonly T[], ids: readonly string[]): T[] {
  const set = new Set(ids)
  return items.filter((item) => set.has(item.strategyId))
}

export function versionLabel(version: number): string {
  return `v${version}`
}

/*
 * 전략을 고르는 셀렉트에 올릴 것. 최신 버전만 올려 같은 이름이 여러 번 보이지 않게 한다.
 * 상세에서 옛 버전으로 들어왔으면 그 버전도 함께 올려 고른 것이 사라지지 않게 한다.
 */
export function pickerOptions(strategies: readonly Strategy[], includeId?: string | null): Strategy[] {
  const heads = groupByLineage(strategies).map((lineage) => lineage.head)
  if (includeId === undefined || includeId === null || heads.some((head) => head.id === includeId)) {
    return heads
  }
  const included = strategies.find((strategy) => strategy.id === includeId)
  return included === undefined ? heads : [...heads, included]
}

/*
 * 화면에 적는 전략 표기. 이름과 버전을 따로 들고 가서 StrategyName 이 버전만 흐리게 그린다.
 * 지워진 전략처럼 버전을 모르는 자리는 null 이고 이름만 적힌다.
 */
export type StrategyLabel = { name: string; version: number | null }

export function labelOf(strategy: Pick<Strategy, 'name' | 'version'>): StrategyLabel {
  return { name: strategy.name, version: strategy.version }
}

/* 목록 응답에는 전략 이름이 없어 전략 목록에서 찾는다. 없으면 대신 적을 이름만 돌려준다. */
export function strategyLabelOf(strategies: readonly Strategy[], strategyId: string, fallback: string): StrategyLabel {
  const found = strategies.find((strategy) => strategy.id === strategyId)
  return found === undefined ? { name: fallback, version: null } : labelOf(found)
}

/*
 * 실행 기록에 적는 전략 표기. 이름을 고치면 새 버전이 생기므로 이름은 같은 뿌리의 최신 버전에서,
 * 버전은 실행이 돌고 있는 그 버전에서 가져온다. 대회 순위표와 같은 규칙이다.
 */
export function latestLabels(strategies: readonly Strategy[]): Map<string, StrategyLabel> {
  const labels = new Map<string, StrategyLabel>()
  for (const lineage of groupByLineage(strategies)) {
    for (const version of lineage.versions) {
      labels.set(version.id, { name: lineage.head.name, version: version.version })
    }
  }
  return labels
}
