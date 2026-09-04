import json
from collections import Counter
from pathlib import Path

from app.core.schema import Strategy


GOLDEN_CASES_PATH = Path(__file__).parent / "fixtures" / "golden_cases.json"


def test_golden_cases_cover_all_user_inputs_and_validate_exact_strategies():
    cases = json.loads(GOLDEN_CASES_PATH.read_text(encoding="utf-8"))

    assert len(cases) == 34
    assert {case["id"] for case in cases} == {f"U-{number:02d}" for number in range(1, 35)}
    assert Counter(case["comparison"] for case in cases) == {
        "exact": 24,
        "valid-with-assumptions": 6,
        "out-of-scope": 3,
        "short-circuit": 1,
    }
    for case in cases:
        assert isinstance(case["shortCircuit"], bool)
        if case["comparison"] == "exact":
            Strategy.model_validate(case["expectedStrategy"])
            assert case["corePaths"]
        if case["shortCircuit"]:
            assert case["expectedStrategy"] is None
            assert case["expectedAttempts"] == 0
