from pathlib import Path

import pytest

from tests.claim_runner import candles_for_days, evaluate_claim_case, factor_operands, load_claim_cases


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


def strategy_with(factor: str, period: int, timeframe: str = "1D") -> dict:
    return {
        "timeframe": timeframe,
        "filters": {
            "type": "GROUP",
            "logic": "AND",
            "children": [{
                "type": "CONDITION",
                "left": {"type": "FACTOR", "factor": factor, "period": period},
                "operator": "GT",
                "right": {"type": "VALUE", "value": 0},
            }],
        },
        "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
        "selection": {"topN": 5},
        "rebalance": {"period": "WEEKLY"},
    }


def test_a_day_count_becomes_candles_of_the_chosen_timeframe():
    assert candles_for_days(365, "1D") == 365
    assert candles_for_days(10, "4H") == 60
    assert candles_for_days(2, "1H") == 48


def test_both_filter_and_ranking_operands_are_collected():
    operands = factor_operands(strategy_with("VOLATILITY", 250))

    assert ("VOLATILITY", 250) in operands
    assert ("RETURN", 20) in operands


def test_the_period_claim_passes_when_the_sentence_period_is_used():
    case = {"id": "C1-11", "claim": "period", "days": 250}

    assert evaluate_claim_case(case, strategy_with("VOLATILITY", 250), [])["passed"]


def test_the_period_claim_fails_when_a_default_period_wins():
    case = {"id": "C1-11", "claim": "period", "days": 250}

    result = evaluate_claim_case(case, strategy_with("VOLATILITY", 20), [])

    assert not result["passed"]
    assert "250" in result["reason"]


def test_the_no_approximation_claim_fails_when_a_forbidden_factor_is_used():
    case = {"id": "C2-01", "claim": "no-approximation", "forbiddenFactors": ["VOLUME_RATIO"]}

    result = evaluate_claim_case(case, strategy_with("VOLUME_RATIO", 20), ["거래대금은 표현할 수 없습니다."])

    assert not result["passed"]


def test_the_no_approximation_claim_fails_when_nothing_is_said():
    case = {"id": "C2-05", "claim": "no-approximation", "forbiddenFactors": []}

    result = evaluate_claim_case(case, strategy_with("RETURN", 20), [])

    assert not result["passed"]
    assert result["reason"] == "silent"


def test_the_no_approximation_claim_passes_when_the_limit_is_stated():
    case = {"id": "C2-05", "claim": "no-approximation", "forbiddenFactors": []}

    result = evaluate_claim_case(case, strategy_with("RETURN", 20), ["손절은 표현할 수 없습니다."])

    assert result["passed"]


def test_the_synonym_claim_passes_when_the_expected_factor_appears():
    case = {"id": "C3-08", "claim": "synonym", "expectedFactor": "VOLATILITY"}

    assert evaluate_claim_case(case, strategy_with("VOLATILITY", 20), [])["passed"]


def test_a_missing_strategy_fails_every_claim():
    case = {"id": "C3-08", "claim": "synonym", "expectedFactor": "VOLATILITY"}

    result = evaluate_claim_case(case, None, [])

    assert not result["passed"]
    assert result["reason"] == "no-strategy"


def test_an_unknown_claim_is_rejected_rather_than_counted_as_a_pass():
    with pytest.raises(ValueError):
        evaluate_claim_case({"id": "X", "claim": "새 명제"}, strategy_with("RETURN", 20), [])
