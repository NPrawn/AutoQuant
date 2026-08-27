import json
import logging

from app.core.logging import (
    JsonFormatter,
    log_llm_call,
    reset_request_id,
    set_request_id,
)


def test_llm_call_log_contains_request_metrics(caplog):
    context_token = set_request_id("request-42")
    try:
        with caplog.at_level(logging.INFO, logger="app"):
            log_llm_call(attempt=2, duration_ms=125.678, tokens=341, success=True)
    finally:
        reset_request_id(context_token)

    record = next(record for record in caplog.records if record.message == "llm_call")
    assert record.request_id == "request-42"
    assert record.attempt == 2
    assert record.duration_ms == 125.68
    assert record.tokens == 341
    assert record.success is True


def test_json_formatter_includes_structured_log_fields():
    record = logging.makeLogRecord(
        {
            "name": "app.llm",
            "levelno": logging.INFO,
            "levelname": "INFO",
            "msg": "llm_call",
            "args": (),
            "request_id": "request-42",
            "attempt": 2,
            "duration_ms": 125.68,
            "tokens": 341,
            "success": True,
        }
    )

    payload = json.loads(JsonFormatter().format(record))

    assert payload["message"] == "llm_call"
    assert payload["request_id"] == "request-42"
    assert payload["attempt"] == 2
    assert payload["duration_ms"] == 125.68
    assert payload["tokens"] == 341
    assert payload["success"] is True
