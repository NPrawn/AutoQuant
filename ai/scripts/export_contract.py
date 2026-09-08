"""계약 산출물을 코드에서 뽑아 `docs/contract/` 아래에 쓴다.

백엔드와 프론트가 우리 코드를 읽지 않고도 계약을 알게 하는 것이 목적이다. 손으로 적은
필드명이 하나도 없어야 한다 — OpenAPI 는 FastAPI 앱에서, DSL 스키마는 Pydantic 모델에서,
예시는 타입 객체를 직렬화해서 만든다. 스키마가 바뀌면 이 스크립트가 생성 단계에서 터진다.

실행:
    python scripts/export_contract.py
"""

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CONTRACT_DIRECTORY = REPOSITORY_ROOT / "docs" / "contract"

if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from app.core.errors import DSLValidationError  # noqa: E402
from app.core.schema import (  # noqa: E402
    ComparisonOperator,
    Condition,
    ErrorResponse,
    FactorOperand,
    FactorType,
    FilterGroup,
    Logic,
    Ranking,
    RankingDirection,
    Rebalance,
    RebalancePeriod,
    Selection,
    Strategy,
    Timeframe,
    TranslateMeta,
    TranslateResponse,
    ValueOperand,
)

# 생성 정보를 담는 키. 재생성 확인 테스트는 이 키를 빼고 비교한다 —
# 커밋 해시는 돌릴 때마다 달라지므로 그대로 비교하면 매번 어긋난다.
GENERATION_KEY = "x-pintect-generation"
SCHEMA_GENERATION_KEY = "$comment"
EXAMPLES_GENERATION_KEY = "_generation"

STATUS = "제안이며 확정 명세가 아니다. 전략 도메인 명세가 바뀌면 이 계약도 바뀐다."

OPENAPI_FILE = "openapi.json"
STRATEGY_SCHEMA_FILE = "strategy-dsl.schema.json"
EXAMPLES_FILE = "examples.json"

MODEL_NAME = "gpt-5.4-mini"


def build_openapi() -> dict[str, Any]:
    """FastAPI 앱이 스스로 만든 OpenAPI 명세."""
    from app.main import app

    return app.openapi()


def build_strategy_schema() -> dict[str, Any]:
    """전략 DSL 의 JSON Schema. 정의처는 `app/core/schema.py` 하나다."""
    return Strategy.model_json_schema()


def build_examples() -> dict[str, Any]:
    """응답 예시 다섯. 전부 타입 객체를 직렬화한 것이라 스키마와 갈라지지 않는다."""
    return {
        "examples": [
            {
                "name": "단순 랭킹 요청",
                "description": "가장 많이 오른 종목을 담는 전략.",
                "response": _response(
                    _momentum_strategy(),
                    assumptions=[
                        "기본 시간프레임 1D를 사용했습니다.",
                        "기본 상위 선택 수 topN 5를 사용했습니다.",
                    ],
                ),
            },
            {
                "name": "조건이 여럿 붙은 요청",
                "description": "여러 필터를 AND 로 쌓은 전략.",
                "response": _response(
                    _stacked_strategy(),
                    assumptions=[
                        "과매도는 RSI 14 기준 30 미만으로 해석했습니다.",
                        "거래량 급증은 VOLUME_RATIO 20 기준 1.5배 이상으로 해석했습니다.",
                    ],
                ),
            },
            {
                "name": "편집 요청",
                "description": "현재 전략을 함께 보내 일부만 고친 결과. changes 가 채워진다.",
                "response": _response(
                    _edited_strategy(),
                    assumptions=["요청한 시간프레임만 바꾸고 나머지는 유지했습니다."],
                    changes=["timeframe 값이 바뀌었습니다."],
                ),
            },
            {
                "name": "짧은 경로",
                "description": (
                    "전략 설명이 아닌 입력. LLM 을 부르지 않고 사유만 돌려준다. "
                    "오류가 아니라 200 이며 strategy 가 null 이다."
                ),
                "response": _short_circuit_response(),
            },
            {
                "name": "오류 응답",
                "description": (
                    "모든 오류가 code·message·detail 세 필드를 가진다. "
                    "code 는 기계가 분기하는 값이라 안정적으로 유지한다."
                ),
                "statusCode": DSLValidationError.status_code,
                "response": _validation_error_response(),
            },
        ]
    }


def _momentum_strategy() -> Strategy:
    return Strategy(
        timeframe=Timeframe.DAY_1,
        filters=FilterGroup(
            type="GROUP",
            logic=Logic.AND,
            children=[
                Condition(
                    type="CONDITION",
                    left=FactorOperand(type="FACTOR", factor=FactorType.RETURN, period=20),
                    operator=ComparisonOperator.GT,
                    right=ValueOperand(type="VALUE", value=0),
                )
            ],
        ),
        ranking=Ranking(factor=FactorType.RETURN, period=20, direction=RankingDirection.DESC),
        selection=Selection(topN=5),
        rebalance=Rebalance(period=RebalancePeriod.WEEKLY),
    )


def _stacked_strategy() -> Strategy:
    return Strategy(
        timeframe=Timeframe.DAY_1,
        filters=FilterGroup(
            type="GROUP",
            logic=Logic.AND,
            children=[
                Condition(
                    type="CONDITION",
                    left=FactorOperand(type="FACTOR", factor=FactorType.RSI, period=14),
                    operator=ComparisonOperator.LT,
                    right=ValueOperand(type="VALUE", value=30),
                ),
                Condition(
                    type="CONDITION",
                    left=FactorOperand(type="FACTOR", factor=FactorType.VOLUME_RATIO, period=20),
                    operator=ComparisonOperator.GTE,
                    right=ValueOperand(type="VALUE", value=1.5),
                ),
            ],
        ),
        ranking=Ranking(factor=FactorType.RETURN, period=20, direction=RankingDirection.DESC),
        selection=Selection(topN=5),
        rebalance=Rebalance(period=RebalancePeriod.WEEKLY),
    )


def _edited_strategy() -> Strategy:
    return _momentum_strategy().model_copy(update={"timeframe": Timeframe.HOUR_4})


def _response(
    strategy: Strategy,
    *,
    assumptions: list[str],
    changes: list[str] | None = None,
) -> dict[str, Any]:
    return TranslateResponse(
        strategy=strategy,
        meta=TranslateMeta(
            attempts=1,
            model=MODEL_NAME,
            elapsedMs=1984,
            warnings=[],
            assumptions=assumptions,
            changes=changes or [],
        ),
    ).model_dump(mode="json")


def _short_circuit_response() -> dict[str, Any]:
    return TranslateResponse(
        strategy=None,
        meta=TranslateMeta(
            attempts=0,
            model=MODEL_NAME,
            elapsedMs=1,
            warnings=[],
            assumptions=[
                "입력이 짧아 전략을 만들지 않았습니다. 어떤 코인을 어떤 기준으로 사고 싶은지 말해 주세요."
            ],
            changes=[],
        ),
    ).model_dump(mode="json")


def _validation_error_response() -> dict[str, Any]:
    return ErrorResponse(
        code=DSLValidationError.code,
        message=DSLValidationError.default_message,
        detail={"attempts": 3, "lastErrors": ["filters.children.0.operator: 허용되지 않는 값입니다."]},
    ).model_dump(mode="json")


def generation_stamp() -> dict[str, str]:
    return {
        "generatedBy": "scripts/export_contract.py",
        "commit": _current_commit(),
        "status": STATUS,
    }


def _current_commit() -> str:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            text=True,
            check=True,
        )
    except (OSError, subprocess.CalledProcessError):
        return "unknown"
    return completed.stdout.strip() or "unknown"


def export(directory: Path = CONTRACT_DIRECTORY) -> list[Path]:
    """산출물을 쓰고 쓴 경로를 돌려준다."""
    directory.mkdir(parents=True, exist_ok=True)
    stamp = generation_stamp()

    documents = {
        OPENAPI_FILE: (build_openapi(), GENERATION_KEY, stamp),
        STRATEGY_SCHEMA_FILE: (build_strategy_schema(), SCHEMA_GENERATION_KEY, json.dumps(stamp, ensure_ascii=False)),
        EXAMPLES_FILE: (build_examples(), EXAMPLES_GENERATION_KEY, stamp),
    }

    written = []
    for filename, (payload, key, value) in documents.items():
        path = directory / filename
        path.write_text(
            json.dumps({key: value, **payload}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        written.append(path)
    return written


if __name__ == "__main__":
    for written_path in export():
        print(written_path.relative_to(REPOSITORY_ROOT))
