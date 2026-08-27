"""Structured application logging with request correlation."""

import json
import logging
from contextvars import ContextVar, Token
from datetime import UTC, datetime
from typing import Any


_request_id: ContextVar[str | None] = ContextVar("request_id", default=None)
_logger = logging.getLogger("app")


class JsonFormatter(logging.Formatter):
    """Render application log records as one JSON object per line."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for field in (
            "request_id",
            "method",
            "path",
            "status_code",
            "duration_ms",
            "attempt",
            "tokens",
            "success",
        ):
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        return json.dumps(payload, ensure_ascii=False)


def configure_logging() -> None:
    """Configure JSON output only when the process has no logging handler yet."""
    root_logger = logging.getLogger()
    if root_logger.handlers:
        return

    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root_logger.addHandler(handler)
    root_logger.setLevel(logging.INFO)


def set_request_id(request_id: str) -> Token[str | None]:
    return _request_id.set(request_id)


def reset_request_id(token: Token[str | None]) -> None:
    _request_id.reset(token)


def current_request_id() -> str | None:
    return _request_id.get()


def log_request_completed(
    *, method: str, path: str, status_code: int, duration_ms: float
) -> None:
    _logger.info(
        "request_completed",
        extra={
            "request_id": current_request_id(),
            "method": method,
            "path": path,
            "status_code": status_code,
            "duration_ms": round(duration_ms, 2),
        },
    )


def log_llm_call(
    *, attempt: int, duration_ms: float, tokens: int | None, success: bool
) -> None:
    """Record LLM-call metrics; F-series code will invoke this around requests."""
    _logger.info(
        "llm_call",
        extra={
            "request_id": current_request_id(),
            "attempt": attempt,
            "duration_ms": round(duration_ms, 2),
            "tokens": tokens,
            "success": success,
        },
    )
