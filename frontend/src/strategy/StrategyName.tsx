import type { StrategyLabel } from './lineage'

type StrategyNameProps = {
  label: StrategyLabel
}

/* 전략 이름 뒤에 버전을 흐린 고정폭 글자로 붙인다. 셀렉트, 이력 표, 결과 줄, 카드가 같은 표기를 쓴다. */
export function StrategyName({ label }: StrategyNameProps) {
  return (
    <>
      {label.name}
      {label.version !== null && <span className="ver-dim">v{label.version}</span>}
    </>
  )
}
