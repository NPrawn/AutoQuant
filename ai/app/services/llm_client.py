"""Gateway boundary for LLM completion requests."""

from collections.abc import Mapping, Sequence
import logging
from time import perf_counter

from openai import APIError, APITimeoutError, OpenAI

from app.core.errors import LLMTimeoutError, LLMUpstreamError
from app.core.logging import log_llm_call


_logger = logging.getLogger(__name__)


def complete(
    *, messages: Sequence[Mapping[str, str]], timeout_seconds: float, attempt: int = 1
) -> tuple[str, int | None]:
    """Return the completion text and total token count for one request."""
    from app.core.config import settings

    started_at = perf_counter()
    tokens: int | None = None
    success = False
    try:
        client = OpenAI(
            api_key=settings.llm_api_key.get_secret_value(),
            base_url=settings.llm_base_url,
            max_retries=0,
        )
        response = client.chat.completions.create(
            model=settings.llm_model,
            messages=list(messages),
            timeout=timeout_seconds,
        )
        tokens = response.usage.total_tokens if response.usage is not None else None
        success = True
        return response.choices[0].message.content or "", tokens
    except APITimeoutError as error:
        _logger.exception("llm_request_timed_out")
        raise LLMTimeoutError() from error
    except APIError as error:
        _logger.exception("llm_request_failed")
        raise LLMUpstreamError() from error
    finally:
        log_llm_call(
            attempt=attempt,
            duration_ms=(perf_counter() - started_at) * 1000,
            tokens=tokens,
            success=success,
        )
