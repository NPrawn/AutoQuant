import { useMemo } from 'react'

import { useChartAssets } from '../market/queries'
import type { ChartAsset } from '../market/types'

/* 모의투자 응답의 종목은 식별자만 온다. 차트가 쓰는 /assets 목록으로 마켓 코드와 이름을 맞춘다. */
export type AssetLookup = ReadonlyMap<string, ChartAsset>

export function useAssetLookup(): AssetLookup {
  const assets = useChartAssets()
  return useMemo(() => new Map((assets.data ?? []).map((asset) => [asset.id, asset])), [assets.data])
}

/* 목록에 없는 종목(상장 폐지 등)은 식별자 앞부분으로 보여 빈칸을 남기지 않는다. */
export function assetCode(lookup: AssetLookup, assetId: string): string {
  return lookup.get(assetId)?.marketCode ?? `종목 ${assetId.slice(0, 8)}`
}

export function assetName(lookup: AssetLookup, assetId: string): string | null {
  return lookup.get(assetId)?.name ?? null
}
