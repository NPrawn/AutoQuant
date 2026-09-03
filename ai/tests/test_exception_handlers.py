import importlib
import sys
from uuid import UUID

import pytest
from fastapi import Body
from fastapi.testclient import TestClient
from pydantic import BaseModel, Field

from app.core.errors import (
    DSLValidationError,
    LLMTimeoutError,
    LLMUpstreamError,
    LLMCreditLimitError,
    PromptTooLongError,
)


class ValidationRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)


def create_app(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://gms.ssafy.io/gmsapi/api.openai.com/v1")
    monkeypatch.setenv("LLM_API_KEY", "test-secret-key")
    monkeypatch.setenv("LLM_MODEL", "gpt-5.4-mini")
    monkeypatch.setenv("APP_ENV", "test")
    for module_name in ("app.main", "app.api.v1.health", "app.core.config"):
        sys.modules.pop(module_name, None)
    return importlib.import_module("app.main").app


@pytest.mark.parametrize(
    ("error_type", "status_code", "code"),
    [
        (LLMTimeoutError, 504, "LLM_TIMEOUT"),
        (LLMUpstreamError, 502, "LLM_UPSTREAM_ERROR"),
        (DSLValidationError, 422, "DSL_VALIDATION_FAILED"),
        (PromptTooLongError, 400, "INVALID_REQUEST"),
        (LLMCreditLimitError, 429, "LLM_CREDIT_LIMIT_EXCEEDED"),
    ],
)
def test_domain_errors_follow_the_spring_error_contract(
    monkeypatch, error_type, status_code, code
):
    path = f"/__test/domain-error/{code.lower()}"
    app = create_app(monkeypatch)

    def raise_domain_error():
        raise error_type("request failed")

    app.add_api_route(path, raise_domain_error)
    response = TestClient(app, raise_server_exceptions=False).get(path)

    assert response.status_code == status_code
    assert response.json() == {
        "code": code,
        "message": error_type.default_message
        if error_type in (LLMTimeoutError, LLMUpstreamError)
        else "request failed",
        "detail": None,
    }


def test_unexpected_errors_return_a_fixed_safe_response(monkeypatch):
    path = "/__test/unexpected-error"
    app = create_app(monkeypatch)

    def raise_unexpected_error():
        raise RuntimeError("api key must not be exposed")

    app.add_api_route(path, raise_unexpected_error)
    response = TestClient(app, raise_server_exceptions=False).get(path)

    assert response.status_code == 500
    assert response.json() == {
        "code": "INTERNAL_ERROR",
        "message": "Internal server error",
        "detail": None,
    }
    assert UUID(response.headers["X-Request-ID"])


def test_dsl_validation_error_returns_safe_retry_context(monkeypatch):
    path = "/__test/dsl-validation-detail"
    app = create_app(monkeypatch)

    def raise_domain_error():
        raise DSLValidationError(
            "전략 형식이 올바르지 않습니다.",
            attempts=3,
            last_errors=["전략 형식이 올바르지 않습니다. 2개 항목을 확인해 주세요."],
        )

    app.add_api_route(path, raise_domain_error)
    response = TestClient(app, raise_server_exceptions=False).get(path)

    assert response.status_code == 422
    assert response.json()["detail"] == {
        "attempts": 3,
        "lastErrors": ["전략 형식이 올바르지 않습니다. 2개 항목을 확인해 주세요."],
    }


@pytest.mark.parametrize(
    "text",
    [pytest.param("", id="empty"), pytest.param("x" * 2001, id="too-long")],
)
def test_request_validation_errors_follow_the_safe_error_contract(monkeypatch, text):
    path = "/__test/request-validation"
    app = create_app(monkeypatch)

    def accept_request(payload: ValidationRequest = Body()):
        return payload

    app.add_api_route(path, accept_request, methods=["POST"])
    response = TestClient(app, raise_server_exceptions=False).post(path, json={"text": text})

    assert response.status_code == 400
    assert response.json() == {
        "code": "INVALID_REQUEST",
        "message": "Request validation failed",
        "detail": {
            "fields": [
                {
                    "path": "body.text",
                    "reason": "String should have at least 1 character"
                    if text == ""
                    else "String should have at most 2000 characters",
                }
            ]
        },
    }
