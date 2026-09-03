"""Compose prompt, LLM completion, and envelope validation."""

from dataclasses import dataclass
from time import perf_counter

from app.core.schema import Strategy, StrategyEnvelope, TranslateMeta
from app.core.errors import DSLValidationError
from app.services.llm_client import complete
from app.services.prompt_builder import build_messages, build_retry_messages
from app.services.validator import (
    ValidationResult,
    format_user_errors,
    validate_llm_response,
)


@dataclass(frozen=True)
class TranslationResult:
    value: Strategy | None
    meta: TranslateMeta | None
    errors: list[dict[str, object]]


def translate(
    user_input: str, current_strategy: Strategy | None = None
) -> TranslationResult:
    """Translate with validation retries and return a validated strategy result."""
    from app.core.config import settings

    started_at = perf_counter()
    messages = build_messages(user_input, current_strategy)
    result: ValidationResult[StrategyEnvelope] | None = None
    for attempt in range(1, settings.max_retries + 2):
        response_text, _ = complete(
            messages=messages, timeout_seconds=settings.llm_timeout_seconds, attempt=attempt
        )
        result = validate_llm_response(response_text, StrategyEnvelope)
        if result.value is not None:
            return TranslationResult(
                value=result.value.strategy,
                meta=TranslateMeta(
                    attempts=attempt,
                    model=settings.llm_model,
                    elapsedMs=int((perf_counter() - started_at) * 1000),
                    warnings=[],
                    assumptions=result.value.assumptions,
                    changes=describe_changes(current_strategy, result.value.strategy),
                ),
                errors=[],
            )
        if attempt <= settings.max_retries:
            messages = build_retry_messages(
                user_input, response_text, result.errors, current_strategy
            )
    assert result is not None
    raise DSLValidationError(format_user_errors(result.errors))


def describe_changes(previous: Strategy | None, current: Strategy) -> list[str]:
    """Summarize changed top-level strategy fields without recursive diffing."""
    if previous is None:
        return []
    changes = []
    for field in Strategy.model_fields:
        if field == "filters":
            continue
        if getattr(previous, field) != getattr(current, field):
            changes.append(f"{field} 값이 바뀌었습니다.")
    if previous.filters != current.filters:
        changes.append("필터 조건이 바뀌었습니다.")
    return changes
