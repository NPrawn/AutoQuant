import copy

import pytest

from app.core.schema import (
    ComparisonOperator,
    FactorType,
    Logic,
    RankingDirection,
    RebalancePeriod,
    Timeframe,
)


@pytest.fixture
def valid_strategy_payload() -> dict:
    """Return a fresh payload based on the Strategy DSL example."""
    payload = {
        "timeframe": Timeframe.HOUR_4.value,
        "filters": {
            "type": "GROUP",
            "logic": Logic.AND.value,
            "children": [
                {
                    "type": "CONDITION",
                    "left": {
                        "type": "FACTOR",
                        "factor": FactorType.RSI.value,
                        "period": 14,
                    },
                    "operator": ComparisonOperator.LT.value,
                    "right": {"type": "VALUE", "value": 30},
                }
            ],
        },
        "ranking": {
            "factor": FactorType.RETURN.value,
            "period": 20,
            "direction": RankingDirection.DESC.value,
        },
        "selection": {"topN": 5},
        "rebalance": {"period": RebalancePeriod.WEEKLY.value},
    }
    return copy.deepcopy(payload)
