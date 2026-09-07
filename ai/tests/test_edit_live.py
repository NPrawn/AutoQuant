"""Opt-in GMS measurement of the edit path. Run with: python -m pytest -m live -k edit."""

import json
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from app.core.errors import DSLValidationError, LLMCreditLimitError, LLMTimeoutError, LLMUpstreamError
from app.core.schema import Strategy
from app.services.prompt_builder import load_prompt_templates
from app.services.translator import translate
from tests.edit_runner import evaluate_edit_case, load_edit_cases


MIN_CALL_INTERVAL_SECONDS = 2.1
FIXTURES_DIR = Path(__file__).parent / "fixtures"
REPORTS_DIR = Path(__file__).parent.parent / "reports"


@pytest.mark.live
def test_live_edit_cases_write_measurement_report():
    load_prompt_templates()
    cases = load_edit_cases(FIXTURES_DIR)
    results = []
    last_call_at = None

    for case in cases:
        if last_call_at is not None:
            time.sleep(max(0, MIN_CALL_INTERVAL_SECONDS - (time.monotonic() - last_call_at)))
        start = Strategy.model_validate(case["startStrategy"])
        try:
            result = translate(case["userInput"], start)
            last_call_at = time.monotonic()
        except LLMCreditLimitError:
            time.sleep(60)
            result = translate(case["userInput"], start)
            last_call_at = time.monotonic()
        except DSLValidationError as error:
            results.append(_error_result(case, "format", error.attempts))
            continue
        except (LLMTimeoutError, LLMUpstreamError):
            results.append(_error_result(case, "upstream", None))
            continue

        strategy = result.value.model_dump(mode="json") if result.value is not None else None
        score = evaluate_edit_case(case, strategy)
        results.append(
            {
                "id": case["id"],
                "category": case["category"],
                "preservation": case["preservation"],
                "attempts": result.meta.attempts if result.meta is not None else None,
                "elapsedMs": result.meta.elapsedMs if result.meta is not None else None,
                "assumptions": result.meta.assumptions if result.meta is not None else [],
                "changes": result.meta.changes if result.meta is not None else [],
                "failureReason": None,
                "strategy": strategy,
                **score,
            }
        )

    report = _build_report(results)
    REPORTS_DIR.mkdir(exist_ok=True)
    report_path = REPORTS_DIR / f"edit-live-{datetime.now(UTC):%Y%m%dT%H%M%SZ}.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    assert len(results) == 12


def _error_result(case: dict, failure_reason: str, attempts: int | None) -> dict:
    return {
        "id": case["id"],
        "category": case["category"],
        "preservation": case["preservation"],
        "changedOk": False,
        "preservedOk": False,
        "passed": False,
        "changedMismatches": [],
        "preservedMismatches": [],
        "attempts": attempts,
        "elapsedMs": None,
        "strategy": None,
        "assumptions": [],
        "changes": [],
        "failureReason": failure_reason,
    }


def _build_report(results: list[dict]) -> dict:
    elapsed = sorted(result["elapsedMs"] for result in results if result["elapsedMs"] is not None)
    # Preservation is reported over the cases that enforce it: the observe
    # cases exist to show whether one edit cascades, not to be graded on it.
    enforced = [result for result in results if result["preservation"] == "strict"]
    return {
        "summary": {
            "total": len(results),
            "passed": sum(result["passed"] for result in results),
            "changedOk": sum(result["changedOk"] for result in results),
            "preservedOk": sum(result["preservedOk"] for result in enforced),
            "preservationEnforced": len(enforced),
            "medianElapsedMs": _percentile(elapsed, 0.5),
            "p95ElapsedMs": _percentile(elapsed, 0.95),
        },
        "byCategory": _group_counts(results, "category"),
        "preservationBreaks": [
            {"id": result["id"], "mismatches": result["preservedMismatches"]}
            for result in results
            if result["preservedMismatches"]
        ],
        "results": results,
    }


def _group_counts(results: list[dict], key: str) -> dict[str, dict[str, int]]:
    grouped: dict[str, dict[str, int]] = {}
    for result in results:
        group = grouped.setdefault(str(result[key]), {"total": 0, "passed": 0})
        group["total"] += 1
        group["passed"] += int(bool(result["passed"]))
    return grouped


def _percentile(values: list[int], percentile: float) -> int | None:
    if not values:
        return None
    return values[round((len(values) - 1) * percentile)]
