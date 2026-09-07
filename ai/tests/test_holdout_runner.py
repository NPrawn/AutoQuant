from pathlib import Path

import pytest

from tests.holdout_runner import (
    evaluate_holdout_case,
    find_scale_errors,
    load_holdout_cases,
    scope_of,
)


FIXTURES_DIR = Path(__file__).parent / "fixtures"


def strategy_with(factor: str, value: float) -> dict:
    return {
        "timeframe": "1D",
        "filters": {
            "type": "GROUP",
            "logic": "AND",
            "children": [
                {
                    "type": "CONDITION",
                    "left": {"type": "FACTOR", "factor": factor, "period": 20},
                    "operator": "GTE",
                    "right": {"type": "VALUE", "value": value},
                }
            ],
        },
        "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
        "selection": {"topN": 5},
        "rebalance": {"period": "WEEKLY"},
    }


def test_the_holdout_set_loads_with_its_categories():
    cases = load_holdout_cases(FIXTURES_DIR)

    assert len(cases) == 28
    assert cases[0]["id"] == "H-01"
    assert all(case["userInput"] for case in cases)
    assert sum(case["scope"] == "in-scope" for case in cases) == 9
    assert sum(case["scope"] == "out-of-scope" for case in cases) == 19


def test_an_unknown_category_is_rejected_rather_than_counted_as_in_scope():
    with pytest.raises(ValueError):
        scope_of("새 갈래")


@pytest.mark.parametrize("factor", ["RETURN", "SMA_DISPARITY", "VOLATILITY", "DRAWDOWN"])
def test_a_percent_factor_written_as_a_fraction_is_a_scale_error(factor):
    errors = find_scale_errors(strategy_with(factor, 0.05))

    assert len(errors) == 1
    assert errors[0]["factor"] == factor


@pytest.mark.parametrize("value", [5, 0, -20, 1, 100])
def test_a_percent_factor_written_in_percentage_points_is_not_an_error(value):
    assert find_scale_errors(strategy_with("RETURN", value)) == []


def test_a_negative_fraction_is_also_a_scale_error():
    assert len(find_scale_errors(strategy_with("DRAWDOWN", -0.2))) == 1


@pytest.mark.parametrize("factor", ["RSI", "VOLUME_RATIO"])
def test_factors_outside_the_percentage_scale_are_not_judged(factor):
    assert find_scale_errors(strategy_with(factor, 0.5)) == []


def test_scale_errors_are_found_inside_nested_groups():
    strategy = strategy_with("RETURN", 5)
    strategy["filters"]["children"].append({
        "type": "GROUP",
        "logic": "OR",
        "children": [
            {
                "type": "CONDITION",
                "left": {"type": "FACTOR", "factor": "VOLATILITY", "period": 20},
                "operator": "LT",
                "right": {"type": "VALUE", "value": 0.03},
            }
        ],
    })

    errors = find_scale_errors(strategy)

    assert [error["path"] for error in errors] == ["filters.children[1].children[0].right.value"]


def test_a_factor_compared_against_another_factor_is_skipped():
    strategy = strategy_with("SMA_DISPARITY", 5)
    strategy["filters"]["children"][0]["right"] = {
        "type": "FACTOR",
        "factor": "SMA_DISPARITY",
        "period": 20,
    }

    assert find_scale_errors(strategy) == []


def test_a_response_without_a_strategy_fails_the_schema_check_only():
    result = evaluate_holdout_case(None, ["전략을 만들지 않았습니다."], attempts=3)

    assert result["shortCircuit"] is False
    assert result["schemaOk"] is False
    assert result["assumptionsOk"] is True
    assert result["scaleOk"] is True


def test_the_short_circuit_path_is_counted_apart_from_schema_failures():
    """짧은 경로는 LLM을 안 부르고 사유만 돌려주는 정상 동작이다. 실패로 세면 안 된다."""
    result = evaluate_holdout_case(None, ["입력이 짧아 전략을 만들지 않았습니다."], attempts=0)

    assert result["shortCircuit"] is True
    assert result["assumptionsOk"] is True


def test_a_strategy_with_zero_attempts_is_not_a_short_circuit():
    result = evaluate_holdout_case(strategy_with("RETURN", 5), ["설명"], attempts=0)

    assert result["shortCircuit"] is False
    assert result["schemaOk"] is True


def test_a_strategy_with_empty_assumptions_is_flagged():
    result = evaluate_holdout_case(strategy_with("RETURN", 5), [], attempts=1)

    assert result["schemaOk"] is True
    assert result["assumptionsOk"] is False
