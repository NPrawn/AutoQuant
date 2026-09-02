import json

import pytest
from pydantic import ValidationError

from app.core.schema import (
    ComparisonOperator,
    FactorType,
    Logic,
    MAX_PERIOD_1D,
    MAX_PERIOD_1H,
    MAX_PERIOD_4H,
    Timeframe,
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


@pytest.mark.parametrize(
    ("timeframe", "max_period"),
    [
        pytest.param(Timeframe.HOUR_1.value, MAX_PERIOD_1H, id=Timeframe.HOUR_1.value),
        pytest.param(Timeframe.HOUR_4.value, MAX_PERIOD_4H, id=Timeframe.HOUR_4.value),
        pytest.param(Timeframe.DAY_1.value, MAX_PERIOD_1D, id=Timeframe.DAY_1.value),
    ],
)
@pytest.mark.parametrize("period_offset", [-1, 0, 1])
def test_period_uses_timeframe_specific_upper_bounds(
    valid_strategy_payload, timeframe, max_period, period_offset
):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["timeframe"] = timeframe
    period = max_period + period_offset
    payload["ranking"]["period"] = period
    if period <= max_period:
        assert Strategy.model_validate(payload).ranking.period == period
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


@pytest.mark.parametrize("period", [0, 1])
def test_period_rejects_zero_and_accepts_one(valid_strategy_payload, period):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["ranking"]["period"] = period
    if period == 1:
        assert Strategy.model_validate(payload).ranking.period == period
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


@pytest.mark.parametrize("children_count", [0, 1, 5, 6])
def test_group_children_boundaries(valid_strategy_payload, children_count):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["filters"]["children"] = [condition() for _ in range(children_count)]
    if 1 <= children_count <= 5:
        assert len(Strategy.model_validate(payload).filters.children) == children_count
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


@pytest.mark.parametrize("top_n", [0, 1, 30, 31])
def test_top_n_boundaries(valid_strategy_payload, top_n):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["selection"]["topN"] = top_n
    if 1 <= top_n <= 30:
        assert Strategy.model_validate(payload).selection.topN == top_n
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


def test_strategy_rejects_third_group_level(valid_strategy_payload):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["filters"] = {
        "type": "GROUP",
        "logic": "AND",
        "children": [
            {
                "type": "GROUP",
                "logic": "AND",
                "children": [
                    {"type": "GROUP", "logic": "AND", "children": [condition()]}
                ],
            }
        ],
    }
    with pytest.raises(ValidationError, match="Filter depth"):
        Strategy.model_validate(payload)


def test_strategy_rejects_six_conditions(valid_strategy_payload):
    from app.core.schema import Strategy

    payload = valid_strategy_payload
    payload["filters"]["children"] = [
        *[condition() for _ in range(4)],
        {
            "type": "GROUP",
            "logic": "AND",
            "children": [condition(), condition()],
        },
    ]
    with pytest.raises(ValidationError, match="Condition count"):
        Strategy.model_validate(payload)


def test_strategy_json_schema_includes_candle_period_description():
    from app.core.schema import strategy_json_schema

    schema = json.loads(strategy_json_schema())

    assert "candles" in json.dumps(schema).lower()


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
