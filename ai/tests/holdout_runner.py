"""홀드아웃 발화의 기계 판정.

이 세트에는 기대 DSL이 없다. 정답지를 만들면 프롬프트에 반영할 유혹이 생기고 그 순간
홀드아웃이 아니게 된다. 그래서 정답을 몰라도 판정되는 것만 본다 — 스키마를 통과했는가,
`assumptions`가 비어 있지 않은가, 재시도를 몇 번 태웠는가, 퍼센트 팩터의 임계값이
분수로 나갔는가.
"""

import re
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from app.core.schema import FactorType


_INPUT_ROW = re.compile(r"^\| (H-\d{2}) \| (.*?) \| (.*?) \| (.*?) \|")

# 퍼센트포인트로 적는 팩터. 5%는 5이지 0.05가 아니다. RSI는 0~100이고
# VOLUME_RATIO는 배수라 분수 판정에서 뺀다.
PERCENT_FACTORS = frozenset({
    FactorType.RETURN,
    FactorType.SMA_DISPARITY,
    FactorType.VOLATILITY,
    FactorType.DRAWDOWN,
})

# 갈래를 둘로 묶는다. 34건과 분포가 정반대라 하나의 통과율로 뭉치면 아무것도 안 보인다.
IN_SCOPE_CATEGORIES = frozenset({"조건이 붙은 요청", "여러 조건을 쌓는 요청", "모호한 요청", "단순 랭킹 요청"})
OUT_OF_SCOPE_CATEGORIES = frozenset({"지원 범위 밖 요청", "전략이 아닌 입력"})


def load_holdout_cases(fixtures_dir: Path) -> list[dict[str, str]]:
    """홀드아웃 발화를 표에서 읽는다."""
    text = (fixtures_dir / "holdout_inputs.md").read_text(encoding="utf-8")
    cases = []
    for line in text.splitlines():
        match = _INPUT_ROW.match(line)
        if match is None:
            continue
        category = match.group(3).strip()
        cases.append({
            "id": match.group(1),
            "userInput": match.group(2).strip(),
            "category": category,
            "source": match.group(4).strip(),
            "scope": scope_of(category),
        })
    return cases


def scope_of(category: str) -> str:
    if category in IN_SCOPE_CATEGORIES:
        return "in-scope"
    if category in OUT_OF_SCOPE_CATEGORIES:
        return "out-of-scope"
    raise ValueError(f"Unknown holdout category: {category}")


def evaluate_holdout_case(
    strategy: Mapping[str, Any] | None, assumptions: list[str]
) -> dict[str, Any]:
    """한 응답에서 기계로 판정 가능한 것만 본다. assumptions 의 내용은 채점하지 않는다."""
    scale_errors = find_scale_errors(strategy) if strategy is not None else []
    return {
        "schemaOk": strategy is not None,
        "assumptionsOk": bool(assumptions),
        "scaleErrors": scale_errors,
        "scaleOk": not scale_errors,
    }


def find_scale_errors(strategy: Mapping[str, Any]) -> list[dict[str, Any]]:
    """퍼센트 팩터의 임계값이 0과 1 사이면 분수로 적은 것이다. 0은 정상이다."""
    errors: list[dict[str, Any]] = []
    _visit(strategy["filters"], "filters", errors)
    return errors


def _visit(node: Mapping[str, Any], path: str, errors: list[dict[str, Any]]) -> None:
    if "children" in node:
        for index, child in enumerate(node["children"]):
            _visit(child, f"{path}.children[{index}]", errors)
        return

    left = node.get("left", {})
    right = node.get("right", {})
    if "factor" not in left or "value" not in right:
        return
    if FactorType(left["factor"]) not in PERCENT_FACTORS:
        return

    threshold = right["value"]
    if 0 < abs(threshold) < 1:
        errors.append({
            "path": f"{path}.right.value",
            "factor": left["factor"],
            "value": threshold,
        })
