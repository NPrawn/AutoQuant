"""Strategy DSL models and shared validation limits."""

from enum import Enum
import json
from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, model_validator


MAX_FILTER_DEPTH = 2
MAX_CONDITIONS = 5
MAX_GROUP_CHILDREN = 5
MAX_PERIOD_1H = 720
MAX_PERIOD_4H = 540
MAX_PERIOD_1D = 365
MAX_TOP_N = 30
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
    children: list["FilterNode"] = Field(min_length=1, max_length=MAX_GROUP_CHILDREN)


FilterNode = Annotated[Union[FilterGroup, Condition], Field(discriminator="type")]
FilterGroup.model_rebuild()


class Ranking(StrictModel):
    factor: FactorType
    period: int = Field(gt=0, description="Ranking calculation period in candles.")
    direction: RankingDirection


class Selection(StrictModel):
    topN: int = Field(gt=0, le=MAX_TOP_N)


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
        max_periods = {
            Timeframe.HOUR_1: MAX_PERIOD_1H,
            Timeframe.HOUR_4: MAX_PERIOD_4H,
            Timeframe.DAY_1: MAX_PERIOD_1D,
        }
        max_period = max_periods[self.timeframe]
        periods = [self.ranking.period]
        conditions = 0

        def visit(node: FilterNode, depth: int) -> None:
            nonlocal conditions
            if isinstance(node, Condition):
                conditions += 1
                for operand in (node.left, node.right):
                    if isinstance(operand, FactorOperand):
                        periods.append(operand.period)
                return
            if depth > MAX_FILTER_DEPTH:
                raise ValueError(f"Filter depth must not exceed {MAX_FILTER_DEPTH}")
            for child in node.children:
                visit(child, depth + 1)

        visit(self.filters, 1)
        if conditions > MAX_CONDITIONS:
            raise ValueError(f"Condition count must not exceed {MAX_CONDITIONS}")
        if any(period > max_period for period in periods):
            raise ValueError(f"Period must not exceed {max_period} for {self.timeframe.value}")
        return self


def strategy_json_schema() -> str:
    """Return the Strategy JSON Schema for structured LLM output."""
    return json.dumps(Strategy.model_json_schema(), ensure_ascii=False)


class TranslateRequest(StrictModel):
    userInput: str = Field(min_length=1, max_length=MAX_USER_INPUT_LENGTH)
    currentStrategy: Strategy | None = None


class TranslateMeta(StrictModel):
    attempts: int = Field(ge=1)
    model: str = Field(min_length=1)
    elapsedMs: int = Field(ge=0)
    warnings: list[str]
    assumptions: list[str]
    changes: list[str]


class TranslateResponse(StrictModel):
    strategy: Strategy
    meta: TranslateMeta


class ErrorResponse(StrictModel):
    code: str
    message: str
    detail: dict[str, object] | None = None
