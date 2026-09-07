"""전략이 유효하지만 의도대로 돌지 않을 조합을 찾아 경고 문구로 돌려준다.

경고는 검증 실패가 아니다. 응답은 200이고 재시도하지 않는다. 빌더에서 사용자가 직접
만든 조합에도 같은 판정이 필요해서 LLM 출력 여부와 무관하게 전략만 보고 판단한다.
"""

from app.core.schema import (
    ComparisonOperator,
    Condition,
    FACTOR_BOUNDS,
    FactorOperand,
    FilterGroup,
    FilterNode,
    Logic,
    REBALANCE_MINUTES,
    Strategy,
    TIMEFRAME_MINUTES,
    ValueOperand,
)


def inspect_strategy(strategy: Strategy) -> list[str]:
    """전략에서 발견한 경고를 순서대로 돌려준다. 없으면 빈 목록이다."""
    warnings = _inspect_filters(strategy.filters)
    warnings.extend(_inspect_ranking_horizon(strategy))
    return warnings


def _inspect_filters(node: FilterNode) -> list[str]:
    if isinstance(node, Condition):
        return _inspect_condition(node)

    warnings: list[str] = []
    for child in node.children:
        warnings.extend(_inspect_filters(child))
    if node.logic is Logic.AND:
        warnings.extend(_inspect_and_group(node))
    return warnings


def _inspect_condition(condition: Condition) -> list[str]:
    """정의역 때문에 언제나 참이거나 언제나 거짓인 조건을 찾는다."""
    if not isinstance(condition.left, FactorOperand) or not isinstance(condition.right, ValueOperand):
        return []

    factor = condition.left.factor
    lower, upper = FACTOR_BOUNDS[factor]
    threshold = condition.right.value
    label = f"{factor.value} {condition.operator.value} {_format(threshold)}"

    if _always_true(condition.operator, threshold, lower, upper):
        return [f"{label} 조건은 항상 참이라 아무 종목도 걸러내지 않습니다."]
    if _always_false(condition.operator, threshold, lower, upper):
        return [f"{label} 조건은 항상 거짓이라 모든 종목이 걸러집니다."]
    return []


def _always_true(
    operator: ComparisonOperator,
    threshold: float,
    lower: float | None,
    upper: float | None,
) -> bool:
    if operator is ComparisonOperator.GT:
        return lower is not None and threshold < lower
    if operator is ComparisonOperator.GTE:
        return lower is not None and threshold <= lower
    if operator is ComparisonOperator.LT:
        return upper is not None and threshold > upper
    return upper is not None and threshold >= upper


def _always_false(
    operator: ComparisonOperator,
    threshold: float,
    lower: float | None,
    upper: float | None,
) -> bool:
    if operator is ComparisonOperator.GT:
        return upper is not None and threshold >= upper
    if operator is ComparisonOperator.GTE:
        return upper is not None and threshold > upper
    if operator is ComparisonOperator.LT:
        return lower is not None and threshold <= lower
    return lower is not None and threshold < lower


def _inspect_and_group(group: FilterGroup) -> list[str]:
    """같은 팩터·같은 기간에 걸린 상한과 하한이 서로 부딪히거나 겹치는지 본다."""
    warnings = []
    bounds: dict[tuple[str, int], dict[str, float]] = {}

    for child in group.children:
        if not isinstance(child, Condition):
            continue
        if not isinstance(child.left, FactorOperand) or not isinstance(child.right, ValueOperand):
            continue

        key = (child.left.factor.value, child.left.period)
        seen = bounds.setdefault(key, {})
        threshold = child.right.value
        label = f"{key[0]}({key[1]})"

        if child.operator in (ComparisonOperator.GT, ComparisonOperator.GTE):
            if "lower" in seen:
                warnings.append(
                    f"{label} 하한 조건이 두 개라 느슨한 쪽({_format(min(seen['lower'], threshold))})은 아무 역할도 하지 않습니다."
                )
            seen["lower"] = max(seen.get("lower", threshold), threshold)
        else:
            if "upper" in seen:
                warnings.append(
                    f"{label} 상한 조건이 두 개라 느슨한 쪽({_format(max(seen['upper'], threshold))})은 아무 역할도 하지 않습니다."
                )
            seen["upper"] = min(seen.get("upper", threshold), threshold)

        if "lower" in seen and "upper" in seen and seen["lower"] > seen["upper"]:
            warnings.append(
                f"{label} 조건이 {_format(seen['lower'])} 이상과 {_format(seen['upper'])} 이하를 동시에 요구해 통과할 종목이 없습니다."
            )
    return warnings


def _inspect_ranking_horizon(strategy: Strategy) -> list[str]:
    """순위를 매기는 관찰 구간이 한 번 담고 있는 기간보다 짧은지 본다."""
    lookback_minutes = strategy.ranking.period * TIMEFRAME_MINUTES[strategy.timeframe]
    holding_minutes = REBALANCE_MINUTES[strategy.rebalance.period]

    if lookback_minutes >= holding_minutes:
        return []
    return [
        f"순위를 {strategy.ranking.period}개 {strategy.timeframe.value} 캔들로 매기는데 "
        f"{strategy.rebalance.period.value} 주기로 담습니다. 관찰 구간이 보유 기간보다 짧습니다."
    ]


def _format(value: float) -> str:
    return f"{value:g}"
