"""Compose prompt, LLM completion, and envelope validation."""

from dataclasses import dataclass
from time import perf_counter

from app.core.schema import Strategy, StrategyEnvelope, TranslateMeta
from app.core.errors import DSLValidationError, LLMCreditLimitError
from app.services.llm_client import complete
from app.services.prompt_builder import build_messages, build_retry_messages
from app.services.validator import (
    ValidationResult,
    format_user_errors,
    validate_llm_response,
)


MAX_LLM_CALLS_PER_MINUTE = 30
SHORT_CIRCUIT_INPUTS = frozenset({"안녕", "안녕하세요", "hi", "hello", "테스트", "test"})
SHORT_CIRCUIT_MAX_LENGTH = 3
_llm_call_times: list[float] = []


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
    if current_strategy is None and _is_short_circuit_input(user_input):
        return TranslationResult(
            value=None,
            meta=TranslateMeta(
                attempts=0,
                model=settings.llm_model,
                elapsedMs=int((perf_counter() - started_at) * 1000),
                warnings=[],
                assumptions=["입력이 짧아 전략을 만들지 않았습니다. 어떤 코인을 어떤 기준으로 사고 싶은지 말해 주세요."],
                changes=[],
            ),
            errors=[],
        )
    messages = build_messages(user_input, current_strategy)
    result: ValidationResult[StrategyEnvelope] | None = None
    for attempt in range(1, settings.max_retries + 2):
        _reserve_llm_call()
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
    error_message = format_user_errors(result.errors)
    raise DSLValidationError(
        error_message,
        attempts=settings.max_retries + 1,
        last_errors=[error_message],
    )


def _is_short_circuit_input(user_input: str) -> bool:
    normalized = user_input.strip().lower()
    return normalized in SHORT_CIRCUIT_INPUTS or len("".join(normalized.split())) <= SHORT_CIRCUIT_MAX_LENGTH


def _reserve_llm_call() -> None:
    now = perf_counter()
    cutoff = now - 60
    _llm_call_times[:] = [called_at for called_at in _llm_call_times if called_at > cutoff]
    if len(_llm_call_times) >= MAX_LLM_CALLS_PER_MINUTE:
        raise LLMCreditLimitError()
    _llm_call_times.append(now)


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
