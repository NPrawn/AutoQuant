"""Opt-in GMS 홀드아웃 측정. 실행: python -m pytest -m live -k holdout

회차당 약 50,000 프롬프트 토큰이라 자주 돌리지 않는다. 평소 회귀 확인은 34건으로 하고
이 세트는 프롬프트를 크게 고친 뒤에만 돌린다.
"""

import json
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from app.core.errors import DSLValidationError, LLMCreditLimitError, LLMTimeoutError, LLMUpstreamError
from app.services.prompt_builder import load_prompt_templates
from app.services.translator import translate
from tests.holdout_runner import evaluate_holdout_case, load_holdout_cases


MIN_CALL_INTERVAL_SECONDS = 2.1
FIXTURES_DIR = Path(__file__).parent / "fixtures"
REPORTS_DIR = Path(__file__).parent.parent / "reports"


@pytest.mark.live
def test_live_holdout_cases_write_measurement_report():
    load_prompt_templates()
    cases = load_holdout_cases(FIXTURES_DIR)
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
            "category": case["category"],
            "scope": case["scope"],
            "attempts": result.meta.attempts if result.meta is not None else None,
            "elapsedMs": result.meta.elapsedMs if result.meta is not None else None,
            "warnings": result.meta.warnings if result.meta is not None else [],
            "assumptions": assumptions,
            "failureReason": None,
            "strategy": strategy,
            **evaluate_holdout_case(strategy, assumptions),
        })

    report = _build_report(results)
    REPORTS_DIR.mkdir(exist_ok=True)
    report_path = REPORTS_DIR / f"holdout-live-{datetime.now(UTC):%Y%m%dT%H%M%SZ}.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    assert len(results) == 28


def _error_result(case: dict, failure_reason: str, attempts: int | None) -> dict:
    return {
        "id": case["id"],
        "category": case["category"],
        "scope": case["scope"],
        "attempts": attempts,
        "elapsedMs": None,
        "warnings": [],
        "assumptions": [],
        "failureReason": failure_reason,
        "strategy": None,
        "schemaOk": False,
        "assumptionsOk": False,
        "scaleErrors": [],
        "scaleOk": True,
    }


def _build_report(results: list[dict]) -> dict:
    return {
        "summary": _counts(results),
        "byScope": {
            scope: _counts([result for result in results if result["scope"] == scope])
            for scope in ("in-scope", "out-of-scope")
        },
        "byCategory": {
            category: _counts([result for result in results if result["category"] == category])
            for category in sorted({result["category"] for result in results})
        },
        "attemptsDistribution": _attempts_distribution(results),
        "scaleErrors": [
            {"id": result["id"], "errors": result["scaleErrors"]}
            for result in results
            if result["scaleErrors"]
        ],
        # 근사해 놓고 아무 말도 안 한 건. 범위 밖에서 제일 위험한 실패다.
        "silentApproximations": [
            result["id"]
            for result in results
            if result["schemaOk"] and not result["assumptionsOk"]
        ],
        "results": results,
    }


def _counts(results: list[dict]) -> dict:
    total = len(results)
    elapsed = sorted(result["elapsedMs"] for result in results if result["elapsedMs"] is not None)
    return {
        "total": total,
        "schemaOk": sum(result["schemaOk"] for result in results),
        "assumptionsOk": sum(result["assumptionsOk"] for result in results),
        "scaleOk": sum(result["scaleOk"] for result in results),
        "schemaOkRate": (sum(result["schemaOk"] for result in results) / total) if total else 0,
        "medianElapsedMs": elapsed[round((len(elapsed) - 1) * 0.5)] if elapsed else None,
    }


def _attempts_distribution(results: list[dict]) -> dict[str, int]:
    distribution: dict[str, int] = {}
    for result in results:
        key = "error" if result["attempts"] is None else str(result["attempts"])
        distribution[key] = distribution.get(key, 0) + 1
    return distribution
