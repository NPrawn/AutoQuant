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
    comparison = case["comparison"]
    if comparison == "short-circuit":
        return strategy is None, None if strategy is None else "value"
    if strategy is None:
        return False, "format"
    if comparison == "exact":
        expected = case["expectedStrategy"]
        for path in case["corePaths"]:
            expected_value = _path_value(expected, path)
            try:
                actual_value = _path_value(strategy, path)
            except (IndexError, KeyError, TypeError):
                return False, "format"
            if actual_value != expected_value:
                return False, _mismatch_type(expected_value, actual_value)
        return True, None
    if comparison in {"valid-with-assumptions", "out-of-scope"}:
        return (True, None) if assumptions else (False, "value")
    raise ValueError(f"Unsupported comparison: {comparison}")


def _path_value(value: Any, path: str) -> Any:
    current = value
    for key, index in _PATH_PARTS.findall(path):
        current = current[int(index)] if index else current[key]
    return current


def _mismatch_type(expected: Any, actual: Any) -> str:
    if isinstance(expected, (int, float)) and isinstance(actual, (int, float)):
        if expected and abs(abs(actual / expected) - 0.01) < 0.001:
            return "scale"
        return "value"
    return "format" if type(expected) is not type(actual) else "value"
