"""Opt-in GMS 명제 측정. 실행: python -m pytest -m live -k claim

프롬프트를 고치기 전과 후에 각각 3회 돌린다. 1회 측정은 모델의 비결정성과
구분되지 않는다.
"""

import json
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from app.core.errors import DSLValidationError, LLMCreditLimitError, LLMTimeoutError, LLMUpstreamError
from app.services.prompt_builder import load_prompt_templates
from app.services.translator import translate
from tests.claim_runner import evaluate_claim_case, load_claim_cases


MIN_CALL_INTERVAL_SECONDS = 2.1
FIXTURES_DIR = Path(__file__).parent / "fixtures"
REPORTS_DIR = Path(__file__).parent.parent / "reports"


@pytest.mark.live
def test_live_claim_cases_write_measurement_report():
    load_prompt_templates()
    cases = load_claim_cases(FIXTURES_DIR)
    results = []
    last_call_at = None

    for case in cases:
        if last_call_at is not None:
            time.sleep(max(0, MIN_CALL_INTERVAL_SECONDS - (time.monotonic() - last_call_at)))
        try:
            result = translate(case["userInput"])
            last_call_at = time.monotonic()
        except LLMCreditLimitError:
            time.sleep(60)
            result = translate(case["userInput"])
            last_call_at = time.monotonic()
        except DSLValidationError as error:
            results.append(_error_result(case, "format", error.attempts))
            continue
        except (LLMTimeoutError, LLMUpstreamError):
            results.append(_error_result(case, "upstream", None))
            continue

        strategy = result.value.model_dump(mode="json") if result.value is not None else None
        assumptions = result.meta.assumptions if result.meta is not None else []
        results.append({
            "id": case["id"],
            "claim": case["claim"],
            "seen": bool(case.get("seen")),
            "userInput": case["userInput"],
            "attempts": result.meta.attempts if result.meta is not None else None,
            "elapsedMs": result.meta.elapsedMs if result.meta is not None else None,
            "assumptions": assumptions,
            "failureReason": None,
            "strategy": strategy,
            **evaluate_claim_case(case, strategy, assumptions),
        })

    report = _build_report(results)
    REPORTS_DIR.mkdir(exist_ok=True)
    report_path = REPORTS_DIR / f"claim-live-{datetime.now(UTC):%Y%m%dT%H%M%SZ}.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    assert len(results) == 36


def _error_result(case: dict, failure_reason: str, attempts: int | None) -> dict:
    return {
        "id": case["id"],
        "claim": case["claim"],
        "seen": bool(case.get("seen")),
        "userInput": case["userInput"],
        "attempts": attempts,
        "elapsedMs": None,
        "assumptions": [],
        "failureReason": failure_reason,
        "strategy": None,
        "passed": False,
        "reason": failure_reason,
    }


def _build_report(results: list[dict]) -> dict:
    unseen = [result for result in results if not result["seen"]]
    return {
        "summary": _counts(results),
        "byClaim": {
            claim: _counts([result for result in results if result["claim"] == claim])
            for claim in ("period", "no-approximation", "synonym")
        },
        "excludingSeen": {
            "summary": _counts(unseen),
            "byClaim": {
                claim: _counts([result for result in unseen if result["claim"] == claim])
                for claim in ("period", "no-approximation", "synonym")
            },
        },
        "failures": [
            {"id": result["id"], "claim": result["claim"], "reason": result["reason"]}
            for result in results
            if not result["passed"]
        ],
        "results": results,
    }


def _counts(results: list[dict]) -> dict:
    passed = sum(result["passed"] for result in results)
    elapsed = sorted(result["elapsedMs"] for result in results if result["elapsedMs"] is not None)
    return {
        "total": len(results),
        "passed": passed,
        "passRate": (passed / len(results)) if results else 0,
        "medianElapsedMs": elapsed[len(elapsed) // 2] if elapsed else None,
    }
