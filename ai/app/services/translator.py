"""Compose prompt, LLM completion, and envelope validation."""

from app.core.schema import Strategy, StrategyEnvelope
from app.services.llm_client import complete
from app.services.prompt_builder import build_messages
from app.services.validator import ValidationResult, validate_llm_response


def translate(
    user_input: str, current_strategy: Strategy | None = None
) -> ValidationResult[StrategyEnvelope]:
    """Run one translation attempt; retry policy is added separately."""
    from app.core.config import settings

    messages = build_messages(user_input, current_strategy)
    response_text, _ = complete(
        messages=messages, timeout_seconds=settings.llm_timeout_seconds, attempt=1
    )
    return validate_llm_response(response_text, StrategyEnvelope)
