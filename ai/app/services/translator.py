"""Compose prompt, LLM completion, and envelope validation."""

from app.core.schema import Strategy, StrategyEnvelope
from app.services.llm_client import complete
from app.services.prompt_builder import build_messages, build_retry_messages
from app.services.validator import ValidationResult, validate_llm_response


def translate(
    user_input: str, current_strategy: Strategy | None = None
) -> ValidationResult[StrategyEnvelope]:
    """Run one translation attempt; retry policy is added separately."""
    from app.core.config import settings

    messages = build_messages(user_input, current_strategy)
    result: ValidationResult[StrategyEnvelope] | None = None
    for attempt in range(1, settings.max_retries + 2):
        response_text, _ = complete(
            messages=messages, timeout_seconds=settings.llm_timeout_seconds, attempt=attempt
        )
        result = validate_llm_response(response_text, StrategyEnvelope)
        if result.value is not None:
            return result
        if attempt <= settings.max_retries:
            messages = build_retry_messages(user_input, response_text, result.errors)
    assert result is not None
    return result


def describe_changes(previous: Strategy | None, current: Strategy) -> list[str]:
    """Summarize changed top-level strategy fields without recursive diffing."""
    if previous is None:
        return ["전략을 새로 생성했습니다."]
    changes = []
    for field in ("timeframe", "ranking", "selection", "rebalance"):
        if getattr(previous, field) != getattr(current, field):
            changes.append(f"{field} 값이 바뀌었습니다.")
    if previous.filters != current.filters:
        changes.append("필터 조건이 바뀌었습니다.")
    return changes
