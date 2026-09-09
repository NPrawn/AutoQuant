from pathlib import Path

from tests.claim_runner import load_claim_cases


FIXTURES_DIR = Path(__file__).parent / "fixtures"


def test_the_claim_set_loads_with_twelve_cases_per_claim():
    cases = load_claim_cases(FIXTURES_DIR)

    assert len(cases) == 36
    assert all(case["userInput"] for case in cases)
    assert sum(case["claim"] == "period" for case in cases) == 12
    assert sum(case["claim"] == "no-approximation" for case in cases) == 12
    assert sum(case["claim"] == "synonym" for case in cases) == 12


def test_every_case_carries_the_field_its_claim_is_scored_on():
    required = {
        "period": "days",
        "no-approximation": "forbiddenFactors",
        "synonym": "expectedFactor",
    }

    for case in load_claim_cases(FIXTURES_DIR):
        assert required[case["claim"]] in case, case["id"]


def test_the_two_cases_moved_from_the_holdout_are_marked_seen():
    seen = [case["id"] for case in load_claim_cases(FIXTURES_DIR) if case.get("seen")]

    assert sorted(seen) == ["C1-12", "C2-12"]
