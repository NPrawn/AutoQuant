import importlib
import sys

import pytest
from fastapi.testclient import TestClient

from app.core.errors import (
    DSLValidationError,
    LLMTimeoutError,
    LLMUpstreamError,
    PromptTooLongError,
)


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
        "message": "request failed",
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
