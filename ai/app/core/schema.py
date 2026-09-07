"""Strategy DSL models and shared validation limits."""

from enum import Enum
import json
from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, model_validator


# Parser defense limit, not a strategy-domain rule.
MAX_FILTER_DEPTH = 5
MAX_USER_INPUT_LENGTH = 500


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Timeframe(str, Enum):
    HOUR_1 = "1H"
    HOUR_4 = "4H"
    DAY_1 = "1D"


class FactorType(str, Enum):
    RETURN = "RETURN"
    RSI = "RSI"
    SMA_DISPARITY = "SMA_DISPARITY"
    VOLATILITY = "VOLATILITY"
    VOLUME_RATIO = "VOLUME_RATIO"
    DRAWDOWN = "DRAWDOWN"


class ComparisonOperator(str, Enum):
    GT = "GT"
    GTE = "GTE"
    LT = "LT"
    LTE = "LTE"


class Logic(str, Enum):
    AND = "AND"
    OR = "OR"


class RankingDirection(str, Enum):
    ASC = "ASC"
    DESC = "DESC"


class RebalancePeriod(str, Enum):
    DAILY = "DAILY"
    WEEKLY = "WEEKLY"
    MONTHLY = "MONTHLY"


class FactorOperand(StrictModel):
    type: Literal["FACTOR"]
    factor: FactorType
    period: int = Field(gt=0, description="Factor calculation period in candles.")


class ValueOperand(StrictModel):
    type: Literal["VALUE"]
    value: float


Operand = Annotated[Union[FactorOperand, ValueOperand], Field(discriminator="type")]


class Condition(StrictModel):
    type: Literal["CONDITION"]
    left: Operand
    operator: ComparisonOperator
    right: Operand


class FilterGroup(StrictModel):
    type: Literal["GROUP"]
    logic: Logic
    children: list["FilterNode"] = Field(min_length=1)


FilterNode = Annotated[Union[FilterGroup, Condition], Field(discriminator="type")]
FilterGroup.model_rebuild()


class Ranking(StrictModel):
    factor: FactorType
    period: int = Field(gt=0, description="Ranking calculation period in candles.")
    direction: RankingDirection


class Selection(StrictModel):
    topN: int = Field(gt=0)


class Rebalance(StrictModel):
    period: RebalancePeriod


class Strategy(StrictModel):
    timeframe: Timeframe
    filters: FilterNode
    ranking: Ranking
    selection: Selection
    rebalance: Rebalance

    @model_validator(mode="after")
    def validate_limits(self) -> "Strategy":
        def visit(node: FilterNode, depth: int) -> None:
            if isinstance(node, Condition):
                return
            if depth > MAX_FILTER_DEPTH:
                raise ValueError(f"Filter depth must not exceed {MAX_FILTER_DEPTH}")
            for child in node.children:
                visit(child, depth + 1)

        visit(self.filters, 1)
        return self


# 팩터가 가질 수 있는 값의 범위. (하한, 상한) 이고 None 은 열려 있다는 뜻이다.
# 경고 층이 "항상 참이라 아무것도 거르지 않는 조건"을 여기서 판단한다.
FACTOR_BOUNDS: dict[FactorType, tuple[float | None, float | None]] = {
    FactorType.RETURN: (None, None),
    FactorType.RSI: (0, 100),
    FactorType.SMA_DISPARITY: (None, None),
    FactorType.VOLATILITY: (0, None),
    FactorType.VOLUME_RATIO: (0, None),
    FactorType.DRAWDOWN: (None, 0),
}

TIMEFRAME_MINUTES: dict[Timeframe, int] = {
    Timeframe.HOUR_1: 60,
    Timeframe.HOUR_4: 240,
    Timeframe.DAY_1: 1_440,
}

REBALANCE_MINUTES: dict[RebalancePeriod, int] = {
    RebalancePeriod.DAILY: 1_440,
    RebalancePeriod.WEEKLY: 10_080,
    RebalancePeriod.MONTHLY: 43_200,
}


def strategy_json_schema() -> str:
    """Return the Strategy JSON Schema for structured LLM output."""
    return json.dumps(Strategy.model_json_schema(), ensure_ascii=False)


class StrategyEnvelope(StrictModel):
    strategy: Strategy
    assumptions: list[str]


def strategy_envelope_json_schema() -> str:
    """Return the LLM output envelope JSON Schema."""
    return json.dumps(StrategyEnvelope.model_json_schema(), ensure_ascii=False)


class TranslateRequest(StrictModel):
    userInput: str = Field(min_length=1, max_length=MAX_USER_INPUT_LENGTH)
    currentStrategy: Strategy | None = None


class TranslateMeta(StrictModel):
    attempts: int = Field(ge=0)
    model: str = Field(min_length=1)
    elapsedMs: int = Field(ge=0)
    warnings: list[str]
    assumptions: list[str]
    changes: list[str]


class TranslateResponse(StrictModel):
    strategy: Strategy | None
    meta: TranslateMeta


class ErrorResponse(StrictModel):
    code: str
    message: str
    detail: dict[str, object] | None = None
