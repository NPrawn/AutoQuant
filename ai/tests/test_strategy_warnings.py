import copy

import pytest

from app.core.schema import Strategy
from app.services.strategy_warnings import inspect_strategy


BASE = {
    "timeframe": "1D",
    "filters": {
        "type": "GROUP",
        "logic": "AND",
        "children": [
            {
                "type": "CONDITION",
                "left": {"type": "FACTOR", "factor": "RETURN", "period": 20},
                "operator": "GT",
                "right": {"type": "VALUE", "value": 5},
            }
        ],
    },
    "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
    "selection": {"topN": 5},
    "rebalance": {"period": "WEEKLY"},
}


def strategy(**changes) -> Strategy:
    payload = copy.deepcopy(BASE)
    payload.update(copy.deepcopy(changes))
    return Strategy.model_validate(payload)


def condition(factor: str, operator: str, value: float, period: int = 20) -> dict:
    return {
        "type": "CONDITION",
        "left": {"type": "FACTOR", "factor": factor, "period": period},
        "operator": operator,
        "right": {"type": "VALUE", "value": value},
    }


def group(*children: dict, logic: str = "AND") -> dict:
    return {"type": "GROUP", "logic": logic, "children": list(children)}


def test_a_sound_strategy_has_no_warnings():
    assert inspect_strategy(strategy()) == []


@pytest.mark.parametrize(
    ("factor", "operator", "value"),
    [
        ("VOLATILITY", "GT", -1),
        ("VOLATILITY", "GTE", 0),
        ("VOLUME_RATIO", "GTE", 0),
        ("RSI", "LTE", 100),
        ("RSI", "GTE", 0),
        ("DRAWDOWN", "LTE", 0),
    ],
)
def test_a_condition_the_factor_range_always_satisfies_warns(factor, operator, value):
    warnings = inspect_strategy(strategy(filters=group(condition(factor, operator, value))))

    assert len(warnings) == 1
    assert "항상 참" in warnings[0]


@pytest.mark.parametrize(
    ("factor", "operator", "value"),
    [
        ("RSI", "GT", 100),
        ("RSI", "LT", 0),
        ("VOLATILITY", "LT", 0),
        ("DRAWDOWN", "GT", 0),
    ],
)
def test_a_condition_the_factor_range_never_satisfies_warns(factor, operator, value):
    warnings = inspect_strategy(strategy(filters=group(condition(factor, operator, value))))

    assert len(warnings) == 1
    assert "항상 거짓" in warnings[0]


def test_an_unbounded_factor_never_warns_on_its_threshold():
    assert inspect_strategy(strategy(filters=group(condition("RETURN", "GT", -999)))) == []


def test_conflicting_bounds_on_one_factor_warn():
    warnings = inspect_strategy(strategy(filters=group(
        condition("RETURN", "GT", 5),
        condition("RETURN", "LT", 0),
    )))

    assert any("통과할 종목이 없습니다" in warning for warning in warnings)


def test_a_redundant_lower_bound_warns():
    warnings = inspect_strategy(strategy(filters=group(
        condition("RETURN", "GT", 0),
        condition("RETURN", "GT", 5),
    )))

    assert warnings == ["RETURN(20) 하한 조건이 두 개라 느슨한 쪽(0)은 아무 역할도 하지 않습니다."]


def test_the_same_factor_at_a_different_period_is_not_redundant():
    assert inspect_strategy(strategy(filters=group(
        condition("RETURN", "GT", 0, period=20),
        condition("RETURN", "GT", 5, period=60),
    ))) == []


def test_bounds_that_conflict_only_under_or_do_not_warn():
    assert inspect_strategy(strategy(filters=group(
        condition("RETURN", "GT", 5),
        condition("RETURN", "LT", 0),
        logic="OR",
    ))) == []


def test_a_ranking_window_shorter_than_the_holding_period_warns():
    warnings = inspect_strategy(strategy(
        ranking={"factor": "RETURN", "period": 5, "direction": "DESC"},
        rebalance={"period": "MONTHLY"},
    ))

    assert len(warnings) == 1
    assert "관찰 구간이 보유 기간보다 짧습니다" in warnings[0]


def test_a_ranking_window_matching_the_holding_period_does_not_warn():
    assert inspect_strategy(strategy(
        timeframe="4H",
        ranking={"factor": "RETURN", "period": 42, "direction": "DESC"},
        rebalance={"period": "WEEKLY"},
    )) == []


def test_a_filter_and_ranking_on_the_same_factor_do_not_warn():
    """필터는 문턱이고 랭킹은 통과한 것의 순서라 겹치는 것이 정상이다."""
    assert inspect_strategy(strategy(
        filters=group(condition("RETURN", "GT", 0)),
        ranking={"factor": "RETURN", "period": 20, "direction": "DESC"},
    )) == []


def test_warnings_from_a_nested_group_are_collected():
    warnings = inspect_strategy(strategy(filters=group(
        condition("RETURN", "GT", 5),
        group(condition("VOLATILITY", "GTE", 0), logic="OR"),
    )))

    assert len(warnings) == 1
    assert "항상 참" in warnings[0]
