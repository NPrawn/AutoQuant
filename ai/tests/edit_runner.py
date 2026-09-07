"""Comparison helpers for the edit-path golden cases.

Editing is scored on two questions: did the requested change happen, and did
everything the user did not ask about survive. The second one is why the
preservation check compares every path outside `changedPaths` against the
starting strategy instead of a hand-written expectation.
"""

import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from tests.golden_runner import path_value


def load_edit_cases(fixtures_dir: Path) -> list[dict[str, Any]]:
    """Load the edit-path contracts."""
    return json.loads((fixtures_dir / "edit_cases.json").read_text(encoding="utf-8"))


def flatten(value: Any, prefix: str = "") -> dict[str, Any]:
    """Map every leaf of a strategy to its dotted path."""
    if isinstance(value, Mapping):
        leaves: dict[str, Any] = {}
        for key, child in value.items():
            leaves |= flatten(child, f"{prefix}.{key}" if prefix else key)
        return leaves
    if isinstance(value, list):
        leaves = {}
        for index, child in enumerate(value):
            leaves |= flatten(child, f"{prefix}[{index}]")
        return leaves
    return {prefix: value}


def evaluate_edit_case(
    case: Mapping[str, Any], strategy: Mapping[str, Any] | None
) -> dict[str, Any]:
    """Score one edit result on the change and on what had to stay put."""
    if strategy is None:
        return {
            "changedOk": False,
            "preservedOk": False,
            "passed": False,
            "changedMismatches": [{"path": "strategy", "expected": "strategy", "actual": None}],
            "preservedMismatches": [],
        }

    changed_mismatches = _changed_mismatches(case, strategy)
    preserved_mismatches = _preserved_mismatches(case, strategy)
    changed_ok = not changed_mismatches
    preserved_ok = not preserved_mismatches
    return {
        "changedOk": changed_ok,
        "preservedOk": preserved_ok,
        # An "observe" case records drift without failing on it: whether a
        # change may cascade into neighbouring fields is still undecided.
        "passed": changed_ok and (preserved_ok or case["preservation"] == "observe"),
        "changedMismatches": changed_mismatches,
        "preservedMismatches": preserved_mismatches,
    }


def _changed_mismatches(
    case: Mapping[str, Any], strategy: Mapping[str, Any]
) -> list[dict[str, Any]]:
    mismatches = []
    if "expectedFilters" in case:
        if strategy["filters"] != case["expectedFilters"]:
            mismatches.append(
                {"path": "filters", "expected": case["expectedFilters"], "actual": strategy["filters"]}
            )
    for path, expected in case["changed"].items():
        try:
            actual = path_value(strategy, path)
        except (IndexError, KeyError, TypeError):
            actual = None
        if actual != expected:
            mismatches.append({"path": path, "expected": expected, "actual": actual})
    return mismatches


def _preserved_mismatches(
    case: Mapping[str, Any], strategy: Mapping[str, Any]
) -> list[dict[str, Any]]:
    # Removing a condition shifts the surviving children's indices, so a
    # path-by-path comparison of the filter tree would report changes that are
    # not there. Those cases carry expectedFilters and compare the tree whole.
    compares_filter_tree = "expectedFilters" in case
    changed_paths = set(case["changed"])
    start_leaves = flatten(case["startStrategy"])
    actual_leaves = flatten(strategy)

    mismatches = []
    for path, expected in start_leaves.items():
        if path in changed_paths or (compares_filter_tree and path.startswith("filters")):
            continue
        actual = actual_leaves.get(path, None)
        if actual != expected:
            mismatches.append({"path": path, "expected": expected, "actual": actual})
    for path, actual in actual_leaves.items():
        if path in start_leaves or path in changed_paths:
            continue
        if compares_filter_tree and path.startswith("filters"):
            continue
        mismatches.append({"path": path, "expected": None, "actual": actual})
    return mismatches
