import type { SimulationPosition } from './types'

/*
 * 보유 종목 평가. 응답에는 수량과 평균 매수가만 있어 현재가는 체결 스트림에서 온다.
 * 아직 체결이 오지 않은 종목은 값을 비워 두고, 하나라도 비면 합계도 비운다.
 * 보이는 숫자를 반쯤 맞는 값으로 채우지 않기 위해서다.
 */
export type PositionValuation = {
  assetId: string
  quantity: number
  averageEntryPrice: number
  price: number | null
  value: number | null
  /* 평균 매수가 대비 수익률, 비율이다 */
  pnlRate: number | null
}

export function valuePositions(
  positions: readonly SimulationPosition[],
  prices: ReadonlyMap<string, number>,
): PositionValuation[] {
  return positions.map((position) => {
    const price = prices.get(position.assetId) ?? null
    return {
      assetId: position.assetId,
      quantity: position.quantity,
      averageEntryPrice: position.averageEntryPrice,
      price,
      value: price === null ? null : price * position.quantity,
      pnlRate: price === null || position.averageEntryPrice === 0 ? null : price / position.averageEntryPrice - 1,
    }
  })
}

export function totalPositionValue(valuations: readonly PositionValuation[]): number | null {
  let total = 0
  for (const valuation of valuations) {
    if (valuation.value === null) {
      return null
    }
    total += valuation.value
  }
  return total
}

/* 총 평가자산은 현금과 보유 평가액의 합이다. 평가액을 모르면 합도 모른다. */
export function totalEquity(cashBalance: number, valuations: readonly PositionValuation[]): number | null {
  const positionValue = totalPositionValue(valuations)
  return positionValue === null ? null : cashBalance + positionValue
}
