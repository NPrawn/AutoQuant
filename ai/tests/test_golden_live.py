"""Opt-in GMS measurement suite. Run with: python -m pytest -m live."""

import json
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from app.core.errors import DSLValidationError, LLMCreditLimitError, LLMTimeoutError, LLMUpstreamError
from app.services.prompt_builder import load_prompt_templates
from app.services.translator import translate
from tests.golden_runner import evaluate_case_details, load_cases_and_inputs


MIN_CALL_INTERVAL_SECONDS = 2.1
FIXTURES_DIR = Path(__file__).parent / "fixtures"
REPORTS_DIR = Path(__file__).parent.parent / "reports"


@pytest.mark.live
def test_live_golden_cases_write_measurement_report():
    load_prompt_templates()
    cases, inputs = load_cases_and_inputs(FIXTURES_DIR)
    results = []
    last_call_at = None

    for case in cases:
        if last_call_at is not None and not case["shortCircuit"]:
            time.sleep(max(0, MIN_CALL_INTERVAL_SECONDS - (time.monotonic() - last_call_at)))
        try:
            result = translate(inputs[case["id"]])
            last_call_at = time.monotonic()
        except LLMCreditLimitError:
            time.sleep(60)
            result = translate(inputs[case["id"]])
            last_call_at = time.monotonic()
        except DSLValidationError as error:
            results.append(_error_result(case, "format", error.attempts))
            continue
        except (LLMTimeoutError, LLMUpstreamError):
            results.append(_error_result(case, "upstream", None))
            continue

        strategy = result.value.model_dump(mode="json") if result.value is not None else None
        assumptions = result.meta.assumptions if result.meta is not None else []
        passed, failure_reason, mismatch = evaluate_case_details(case, strategy, assumptions)
        results.append(
            {
                "id": case["id"],
                "category": case["category"],
                "source": case["source"],
                "comparison": case["comparison"],
                "passed": passed,
                "failureReason": failure_reason,
                "attempts": result.meta.attempts if result.meta is not None else None,
                "elapsedMs": result.meta.elapsedMs if result.meta is not None else None,
                "mismatch": mismatch,
            }
        )

    report = _build_report(results)
    REPORTS_DIR.mkdir(exist_ok=True)
    report_path = REPORTS_DIR / f"golden-live-{datetime.now(UTC):%Y%m%dT%H%M%SZ}.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    assert len(results) == 34
    assert report["summary"]["eligible"] == 31


def _error_result(case: dict, failure_reason: str, attempts: int | None) -> dict:
    return {
        "id": case["id"],
        "category": case["category"],
        "source": case["source"],
        "comparison": case["comparison"],
        "passed": False,
        "failureReason": failure_reason,
        "attempts": attempts,
        "elapsedMs": None,
        "mismatch": None,
    }


def _build_report(results: list[dict]) -> dict:
    eligible = [result for result in results if result["comparison"] != "out-of-scope"]
    passed = [result for result in eligible if result["passed"]]
    elapsed = sorted(result["elapsedMs"] for result in eligible if result["elapsedMs"] is not None)
    return {
        "summary": {
            "eligible": len(eligible),
            "passed": len(passed),
            "passRate": len(passed) / len(eligible) if eligible else 0,
            "averageAttempts": sum(result["attempts"] or 0 for result in eligible) / len(eligible),
            "medianElapsedMs": _percentile(elapsed, 0.5),
            "p95ElapsedMs": _percentile(elapsed, 0.95),
        },
        "byCategory": _group_counts(eligible, "category"),
        "bySource": _group_counts(eligible, "source"),
        "bySourceExact": _group_counts(
            [result for result in eligible if result["comparison"] == "exact"], "source"
        ),
        "failureReasons": _group_counts([result for result in eligible if not result["passed"]], "failureReason"),
        "outOfScope": [result["id"] for result in results if result["comparison"] == "out-of-scope"],
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
