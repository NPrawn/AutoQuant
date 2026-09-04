from tests.golden_runner import evaluate_case
from tests.test_golden_live import _build_report


def test_evaluate_case_matches_only_declared_core_paths(valid_strategy_payload):
    case = {
        "comparison": "exact",
        "expectedStrategy": valid_strategy_payload,
        "corePaths": ["ranking.factor", "ranking.period"],
    }
    actual = valid_strategy_payload.copy()
    actual["selection"] = {"topN": 3}

    assert evaluate_case(case, actual, []) == (True, None)


def test_evaluate_case_classifies_hundredfold_numeric_difference_as_scale(valid_strategy_payload):
    case = {
        "comparison": "exact",
        "expectedStrategy": valid_strategy_payload,
        "corePaths": ["filters.children[0].right.value"],
    }
    actual = valid_strategy_payload.copy()
    actual["filters"] = valid_strategy_payload["filters"].copy()
    actual["filters"]["children"] = [valid_strategy_payload["filters"]["children"][0].copy()]
    actual["filters"]["children"][0]["right"] = {"type": "VALUE", "value": 0.3}

    assert evaluate_case(case, actual, []) == (False, "scale")


def test_evaluate_case_classifies_hundredfold_larger_numeric_difference_as_scale(
    valid_strategy_payload,
):
    case = {
        "comparison": "exact",
        "expectedStrategy": valid_strategy_payload,
        "corePaths": ["filters.children[0].right.value"],
    }
    actual = valid_strategy_payload.copy()
    actual["filters"] = valid_strategy_payload["filters"].copy()
    actual["filters"]["children"] = [valid_strategy_payload["filters"]["children"][0].copy()]
    actual["filters"]["children"][0]["right"] = {"type": "VALUE", "value": 3000}

    assert evaluate_case(case, actual, []) == (False, "scale")


def test_evaluate_case_requires_strategy_and_assumptions_for_non_exact_case(valid_strategy_payload):
    case = {"comparison": "valid-with-assumptions"}

    assert evaluate_case(case, valid_strategy_payload, []) == (False, "value")
    assert evaluate_case(case, valid_strategy_payload, ["기본값을 사용했습니다."]) == (True, None)


def test_evaluate_case_classifies_missing_core_path_as_format(valid_strategy_payload):
    case = {
        "comparison": "exact",
        "expectedStrategy": valid_strategy_payload,
        "corePaths": ["filters.children[0].left.factor"],
    }
    actual = valid_strategy_payload.copy()
    actual["filters"] = valid_strategy_payload["filters"]["children"][0]

    assert evaluate_case(case, actual, []) == (False, "format")


def test_build_report_separates_exact_source_results():
    report = _build_report(
        [
            {"comparison": "exact", "source": "team", "category": "conditional", "passed": True, "attempts": 1, "elapsedMs": 100, "failureReason": None},
            {"comparison": "exact", "source": "author", "category": "ranking", "passed": False, "attempts": 1, "elapsedMs": 100, "failureReason": "value"},
            {"comparison": "valid-with-assumptions", "source": "author", "category": "not-strategy", "passed": True, "attempts": 1, "elapsedMs": 100, "failureReason": None},
        ]
    )

    assert report["bySourceExact"] == {
        "team": {"total": 1, "passed": 1},
        "author": {"total": 1, "passed": 0},
    }
