"""Strategy DSL models and shared validation limits."""

from enum import Enum
from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, model_validator


MAX_FILTER_DEPTH = 2
MAX_CONDITIONS = 5
MAX_GROUP_CHILDREN = 5
MAX_PERIOD_1H = 720
MAX_PERIOD_4H = 540
MAX_TOP_N = 30


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Timeframe(str, Enum):
    HOUR_1 = "1H"
    HOUR_4 = "4H"


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
        max_period = MAX_PERIOD_1H if self.timeframe is Timeframe.HOUR_1 else MAX_PERIOD_4H
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
