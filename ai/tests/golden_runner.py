"""Shared comparison helpers for the opt-in live golden-case runner."""

import re
import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any


_PATH_PARTS = re.compile(r"([^.\[\]]+)|\[(\d+)\]")
_INPUT_ROW = re.compile(r"^\| (U-\d{2}) \| (.*?) \|")


def load_cases_and_inputs(fixtures_dir: Path) -> tuple[list[dict[str, Any]], dict[str, str]]:
    """Load golden contracts and their Korean input text from fixture files."""
    cases = json.loads((fixtures_dir / "golden_cases.json").read_text(encoding="utf-8"))
    inputs = {
        match.group(1): match.group(2)
        for line in (fixtures_dir / "user_inputs.md").read_text(encoding="utf-8").splitlines()
        if (match := _INPUT_ROW.match(line))
    }
    return cases, inputs


def evaluate_case(
    case: Mapping[str, Any], strategy: Mapping[str, Any] | None, assumptions: list[str]
) -> tuple[bool, str | None]:
    """Return whether one live result meets its fixture contract."""
    passed, failure_reason, _ = evaluate_case_details(case, strategy, assumptions)
    return passed, failure_reason


def evaluate_case_details(
    case: Mapping[str, Any], strategy: Mapping[str, Any] | None, assumptions: list[str]
) -> tuple[bool, str | None, dict[str, Any] | None]:
    """Return the fixture decision and a compact mismatch for later diagnosis."""
    comparison = case["comparison"]
    if comparison == "short-circuit":
        return strategy is None, None if strategy is None else "value", None
    if strategy is None:
        return False, "format", {"path": "strategy", "expected": "strategy", "actual": None}
    if comparison == "exact":
        expected = case["expectedStrategy"]
        for path in case["corePaths"]:
            expected_value = path_value(expected, path)
            try:
                actual_value = path_value(strategy, path)
            except (IndexError, KeyError, TypeError):
                return False, "format", {"path": path, "expected": expected_value, "actual": None}
            if actual_value != expected_value:
                return False, _mismatch_type(expected_value, actual_value), {
                    "path": path,
                    "expected": expected_value,
                    "actual": actual_value,
                }
        return True, None, None
    if comparison in {"valid-with-assumptions", "out-of-scope"}:
        return (True, None, None) if assumptions else (False, "value", {"path": "assumptions", "expected": "non-empty", "actual": []})
    raise ValueError(f"Unsupported comparison: {comparison}")


def path_value(value: Any, path: str) -> Any:
    current = value
    for key, index in _PATH_PARTS.findall(path):
        current = current[int(index)] if index else current[key]
    return current


def _mismatch_type(expected: Any, actual: Any) -> str:
    if isinstance(expected, (int, float)) and isinstance(actual, (int, float)):
        ratio = abs(actual / expected) if expected else None
        if ratio is not None and (abs(ratio - 0.01) < 0.001 or abs(ratio - 100) < 0.1):
            return "scale"
        return "value"
    return "format" if type(expected) is not type(actual) else "value"
