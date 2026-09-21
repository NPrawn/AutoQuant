import type { Strategy } from './types.ts'

/*
 * 판마다 행이 하나인 전략들을 한 전략으로 묶는다. 서버는 rootStrategyId 와 versionNo 만 주고 묶는 일은 화면이 한다.
 * 목록은 뿌리별 한 줄(최신 판)이고, 상세는 같은 뿌리의 모든 판으로 백테스트·모의투자 기록을 거른다.
 */
export type StrategyLineage = {
  rootId: string
  /* 가장 높은 판. 목록의 대표다. */
  head: Strategy
  /* 판 번호 내림차순. head 가 첫 번째다. */
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

/* 어느 판을 보고 있든 그 전략 전체를 돌려준다. 목록에 없는 전략(삭제됨 등)이면 null 이다. */
export function lineageOf(strategies: readonly Strategy[], strategyId: string): StrategyLineage | null {
  const found = strategies.find((strategy) => strategy.id === strategyId)
  if (found === undefined) {
    return null
  }
  return groupByLineage(strategies.filter((strategy) => strategy.rootId === found.rootId))[0] ?? null
}

/* 기록을 거를 때 쓰는 판 식별자 묶음. 전략 목록을 아직 못 받았으면 보고 있는 판 하나만 쓴다. */
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
