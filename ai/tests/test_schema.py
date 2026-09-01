import pytest
import json
from pydantic import ValidationError


def valid_strategy() -> dict:
    return {
        "timeframe": "4H",
        "filters": {
            "type": "GROUP",
            "logic": "AND",
            "children": [{"type": "CONDITION", "left": {"type": "FACTOR", "factor": "RSI", "period": 14}, "operator": "LT", "right": {"type": "VALUE", "value": 30}}],
        },
        "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
        "selection": {"topN": 5},
        "rebalance": {"period": "WEEKLY"},
    }


def test_strategy_accepts_recursive_filter_tree():
    from app.core.schema import Strategy
    payload = valid_strategy()
    payload["filters"]["children"].append({"type": "GROUP", "logic": "OR", "children": [payload["filters"]["children"][0].copy()]})
    assert Strategy.model_validate(payload).timeframe.value == "4H"


def test_strategy_rejects_unknown_fields_and_daily_timeframe():
    from app.core.schema import Strategy
    payload = valid_strategy()
    payload["timeframe"] = "1D"
    payload["unexpected"] = True
    with pytest.raises(ValidationError):
        Strategy.model_validate(payload)


@pytest.mark.parametrize("period", [0, 1, 720, 721])
def test_period_uses_timeframe_specific_bounds(period):
    from app.core.schema import Strategy
    payload = valid_strategy()
    payload["timeframe"] = "1H"
    payload["ranking"]["period"] = period
    if 1 <= period <= 720:
        assert Strategy.model_validate(payload).ranking.period == period
    else:
        with pytest.raises(ValidationError):
            Strategy.model_validate(payload)


def test_strategy_json_schema_includes_candle_period_description():
    from app.core.schema import strategy_json_schema

    schema = json.loads(strategy_json_schema())

    assert "candles" in json.dumps(schema).lower()
