import copy
from pathlib import Path

from app.core.schema import Strategy
from tests.edit_runner import evaluate_edit_case, flatten, load_edit_cases


FIXTURES_DIR = Path(__file__).parent / "fixtures"


def case(case_id: str) -> dict:
    return next(item for item in load_edit_cases(FIXTURES_DIR) if item["id"] == case_id)


def applied(case_data: dict) -> dict:
    """Build the result an ideal model would return for one case."""
    strategy = copy.deepcopy(case_data["startStrategy"])
    if "expectedFilters" in case_data:
        strategy["filters"] = copy.deepcopy(case_data["expectedFilters"])
    for path, value in case_data["changed"].items():
        keys = path.replace("]", "").replace("[", ".").split(".")
        target = strategy
        for key in keys[:-1]:
            target = target[int(key)] if key.isdigit() else target[key]
        target[keys[-1]] = value
    return strategy


def test_every_edit_case_starts_from_a_valid_strategy():
    cases = load_edit_cases(FIXTURES_DIR)

    assert len(cases) == 13
    assert all(Strategy.model_validate(item["startStrategy"]) for item in cases)


def test_the_ideal_edit_passes_every_case():
    results = [evaluate_edit_case(item, applied(item)) for item in load_edit_cases(FIXTURES_DIR)]

    assert all(result["passed"] for result in results)


def test_a_touched_field_the_user_did_not_mention_fails_preservation():
    case_data = case("E-01")
    strategy = applied(case_data)
    strategy["selection"]["topN"] = 5

    result = evaluate_edit_case(case_data, strategy)

    assert result["changedOk"]
    assert not result["preservedOk"]
    assert result["preservedMismatches"] == [
        {"path": "selection.topN", "expected": 7, "actual": 5}
    ]


def test_a_missing_requested_change_fails_the_change_check():
    case_data = case("E-02")

    result = evaluate_edit_case(case_data, copy.deepcopy(case_data["startStrategy"]))

    assert not result["changedOk"]
    assert result["changedMismatches"] == [
        {"path": "selection.topN", "expected": 10, "actual": 7}
    ]


def test_removing_a_condition_does_not_report_the_shifted_siblings():
    case_data = case("E-09")

    result = evaluate_edit_case(case_data, applied(case_data))

    assert result["passed"]
    assert result["preservedMismatches"] == []


def test_an_observe_case_records_drift_without_failing():
    case_data = case("E-11")
    strategy = applied(case_data)
    strategy["timeframe"] = "1D"

    result = evaluate_edit_case(case_data, strategy)

    assert result["passed"]
    assert not result["preservedOk"]
    assert result["preservedMismatches"] == [
        {"path": "timeframe", "expected": "4H", "actual": "1D"}
    ]


def test_a_rejected_edit_scores_as_a_failure():
    result = evaluate_edit_case(case("E-01"), None)

    assert not result["passed"]
    assert result["changedMismatches"][0]["path"] == "strategy"


def test_flatten_indexes_list_children():
    leaves = flatten({"filters": {"children": [{"type": "CONDITION"}]}})

    assert leaves == {"filters.children[0].type": "CONDITION"}
