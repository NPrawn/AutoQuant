import json

import pytest
from pydantic import ValidationError

from app.core.schema import (
    ComparisonOperator,
    FactorType,
    Logic,
)


def condition() -> dict:
    return {
        "type": "CONDITION",
        "left": {"type": "FACTOR", "factor": FactorType.RSI.value, "period": 14},
        "operator": ComparisonOperator.LT.value,
        "right": {"type": "VALUE", "value": 30},
    }


def test_strategy_accepts_recursive_filter_tree(valid_strategy_payload):
    from app.core.schema import Strategy
    payload = valid_strategy_payload
    payload["filters"]["children"].append(
        {"type": "GROUP", "logic": Logic.OR.value, "children": [condition()]}
    )
    assert Strategy.model_validate(payload).timeframe.value == "4H"


def test_strategy_rejects_unknown_fields(valid_strategy_payload):
    from app.core.schema import Strategy
    payload = valid_strategy_payload
    payload["unexpected"] = True
    with pytest.raises(ValidationError):
        Strategy.model_validate(payload)


@pytest.mark.parametrize("period", [1, 10_000])
def test_ranking_period_accepts_any_positive_value(valid_strategy_payload, period):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["ranking"]["period"] = period
    assert Strategy.model_validate(payload).ranking.period == period


@pytest.mark.parametrize("period", [0, -1])
def test_ranking_period_rejects_non_positive_value(valid_strategy_payload, period):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["ranking"]["period"] = period
    with pytest.raises(ValidationError):
        Strategy.model_validate(payload)


@pytest.mark.parametrize("period", [0, -1])
def test_factor_period_rejects_non_positive_value(period):
    from app.core.schema import FactorOperand

    with pytest.raises(ValidationError):
        FactorOperand(type="FACTOR", factor=FactorType.RSI, period=period)


@pytest.mark.parametrize("children_count", [0, 1, 6])
def test_group_children_require_at_least_one_child(valid_strategy_payload, children_count):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["filters"]["children"] = [condition() for _ in range(children_count)]
    if children_count >= 1:
        assert len(Strategy.model_validate(payload).filters.children) == children_count
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


@pytest.mark.parametrize("top_n", [-1, 0, 1, 100])
def test_top_n_requires_positive_value(valid_strategy_payload, top_n):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["selection"]["topN"] = top_n
    if top_n > 0:
        assert Strategy.model_validate(payload).selection.topN == top_n
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


def nested_group(depth: int) -> dict:
    node = condition()
    for _ in range(depth):
        node = {"type": "GROUP", "logic": "AND", "children": [node]}
    return node


def test_strategy_accepts_five_group_levels(valid_strategy_payload):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["filters"] = nested_group(5)
    assert Strategy.model_validate(payload).filters.type == "GROUP"


def test_strategy_rejects_six_group_levels(valid_strategy_payload):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["filters"] = nested_group(6)
    with pytest.raises(ValidationError, match="Filter depth"):
        Strategy.model_validate(payload)


def test_strategy_json_schema_includes_candle_period_description():
    from app.core.schema import strategy_json_schema

    schema = json.loads(strategy_json_schema())

    assert "candles" in json.dumps(schema).lower()


def test_strategy_envelope_schema_requires_strategy_and_assumptions(valid_strategy_payload):
    from app.core.schema import StrategyEnvelope

    envelope = StrategyEnvelope(strategy=valid_strategy_payload, assumptions=["RSI period=14"])

    assert envelope.assumptions == ["RSI period=14"]


def test_translate_models_use_camel_case_contract_fields(valid_strategy_payload):
    from app.core.schema import Strategy, TranslateMeta, TranslateRequest, TranslateResponse

    request = TranslateRequest(userInput="RSI 전략")
    response = TranslateResponse(
        strategy=Strategy.model_validate(valid_strategy_payload),
        meta=TranslateMeta(
            attempts=1,
            model="gpt-test",
            elapsedMs=120,
            warnings=[],
            assumptions=["period=20"],
            changes=[],
        ),
    )

    assert request.model_dump() == {"userInput": "RSI 전략", "currentStrategy": None}
    assert response.meta.assumptions == ["period=20"]
    assert response.meta.elapsedMs == 120


def test_translate_request_limits_user_input_to_500_characters():
    from app.core.schema import TranslateRequest

    assert TranslateRequest(userInput="가" * 500).userInput == "가" * 500
    with pytest.raises(ValidationError):
        TranslateRequest(userInput="가" * 501)
