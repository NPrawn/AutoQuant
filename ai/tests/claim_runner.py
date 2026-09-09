"""명제 세트의 기계 판정.

정답이 발화 문장 안에 들어 있는 문장만 모았다. "1년"이라고 적힌 요청의 정답
`period=365`는 프롬프트가 가르치는 해석이 아니라 문장을 읽으면 나오는 값이다.
그래서 사람이 라벨을 붙이지 않아도 채점되고, 골든 세트처럼 오염되지 않는다.
"""

import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from app.core.schema import TIMEFRAME_MINUTES, Timeframe


MINUTES_PER_DAY = 1_440


def load_claim_cases(fixtures_dir: Path) -> list[dict[str, Any]]:
    """명제 세트를 읽는다."""
    return json.loads((fixtures_dir / "claim_cases.json").read_text(encoding="utf-8"))


def candles_for_days(days: int, timeframe: str) -> int:
    """문장의 일수를 선택된 봉 개수로 환산한다. 암호화폐는 휴장이 없어 1년이 365봉이다."""
    return days * MINUTES_PER_DAY // TIMEFRAME_MINUTES[Timeframe(timeframe)]


def factor_operands(strategy: Mapping[str, Any]) -> list[tuple[str, int]]:
    """필터 트리와 랭킹에 쓰인 팩터와 기간을 모두 모은다."""
    found: list[tuple[str, int]] = []

    def visit(node: Mapping[str, Any]) -> None:
        if node.get("type") == "GROUP":
            for child in node["children"]:
                visit(child)
            return
        for side in ("left", "right"):
            operand = node[side]
            if operand["type"] == "FACTOR":
                found.append((operand["factor"], operand["period"]))

    visit(strategy["filters"])
    found.append((strategy["ranking"]["factor"], strategy["ranking"]["period"]))
    return found


def evaluate_claim_case(
    case: Mapping[str, Any], strategy: Mapping[str, Any] | None, assumptions: list[str]
) -> dict[str, Any]:
    """한 발화의 결과를 그 발화가 검증하는 명제로 채점한다."""
    if strategy is None:
        return {"passed": False, "reason": "no-strategy"}

    operands = factor_operands(strategy)
    factors = {factor for factor, _ in operands}
    claim = case["claim"]

    if claim == "period":
        wanted = candles_for_days(case["days"], strategy["timeframe"])
        if any(period == wanted for _, period in operands):
            return {"passed": True, "reason": None}
        used = sorted({period for _, period in operands})
        return {"passed": False, "reason": f"expected period {wanted}, got {used}"}

    if claim == "no-approximation":
        leaked = sorted(set(case["forbiddenFactors"]) & factors)
        if leaked:
            return {"passed": False, "reason": f"approximated with {leaked}"}
        if not assumptions:
            return {"passed": False, "reason": "silent"}
        return {"passed": True, "reason": None}

    if claim == "synonym":
        if case["expectedFactor"] in factors:
            return {"passed": True, "reason": None}
        return {"passed": False, "reason": f"expected {case['expectedFactor']}, got {sorted(factors)}"}

    raise ValueError(f"Unknown claim: {claim}")
