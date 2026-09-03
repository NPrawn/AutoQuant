import importlib
import sys

import pytest
from fastapi.testclient import TestClient

from app.core.schema import Strategy, TranslateMeta
from app.core.errors import DSLValidationError, LLMCreditLimitError, LLMTimeoutError, LLMUpstreamError
from app.services.translator import TranslationResult


def create_app(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test-secret-key")
    monkeypatch.setenv("LLM_MODEL", "gpt-test")
    monkeypatch.setenv("APP_ENV", "test")
    for module_name in (
        "app.main",
        "app.api.deps",
        "app.api.v1.strategy",
        "app.core.config",
    ):
        sys.modules.pop(module_name, None)
    return importlib.import_module("app.main").app


def translation_result(valid_strategy_payload) -> TranslationResult:
    return TranslationResult(
        value=Strategy.model_validate(valid_strategy_payload),
        meta=TranslateMeta(
            attempts=1,
            model="gpt-test",
            elapsedMs=12,
            warnings=[],
            assumptions=["RSI period=14"],
            changes=[],
        ),
        errors=[],
    )


def test_translate_endpoint_returns_translator_strategy_and_meta(
    monkeypatch, valid_strategy_payload
):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    app.dependency_overrides[get_translator] = lambda: (
        lambda *_: translation_result(valid_strategy_payload)
    )
    response = TestClient(app).post(
        "/api/v1/ai/strategies/translate", json={"userInput": "RSI 전략"}
    )

    assert response.status_code == 200
    assert response.json()["strategy"] == valid_strategy_payload
    assert response.json()["meta"]["assumptions"] == ["RSI period=14"]
    assert set(response.json()["meta"]) == {
        "attempts", "model", "elapsedMs", "warnings", "assumptions", "changes"
    }


def test_translate_endpoint_passes_current_strategy_to_translator(
    monkeypatch, valid_strategy_payload
):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    received_current_strategies = []

    def fake_translator(_, current_strategy):
        received_current_strategies.append(current_strategy)
        return translation_result(valid_strategy_payload)

    app.dependency_overrides[get_translator] = lambda: fake_translator
    response = TestClient(app).post(
        "/api/v1/ai/strategies/translate",
        json={"userInput": "상위 3개로 변경", "currentStrategy": valid_strategy_payload},
    )

    assert response.status_code == 200
    assert received_current_strategies[0].selection.topN == 5


def test_translate_endpoint_preserves_request_id(monkeypatch, valid_strategy_payload):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    app.dependency_overrides[get_translator] = lambda: (
        lambda *_: translation_result(valid_strategy_payload)
    )
    response = TestClient(app).post(
        "/api/v1/ai/strategies/translate",
        headers={"X-Request-ID": "spring-request-id"},
        json={"userInput": "RSI 전략"},
    )

    assert response.status_code == 200
    assert response.headers["X-Request-ID"] == "spring-request-id"


def test_translate_endpoint_generates_request_id(monkeypatch, valid_strategy_payload):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    app.dependency_overrides[get_translator] = lambda: (
        lambda *_: translation_result(valid_strategy_payload)
    )
    response = TestClient(app).post(
        "/api/v1/ai/strategies/translate", json={"userInput": "RSI 전략"}
    )

    assert response.status_code == 200
    assert response.headers["X-Request-ID"]


def test_translate_endpoint_rejects_invalid_request_with_contract_code(monkeypatch):
    app = create_app(monkeypatch)

    response = TestClient(app, raise_server_exceptions=False).post(
        "/api/v1/ai/strategies/translate", json={"userInput": ""}
    )

    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_REQUEST"


def test_translate_endpoint_rejects_unknown_request_field(monkeypatch):
    app = create_app(monkeypatch)

    response = TestClient(app, raise_server_exceptions=False).post(
        "/api/v1/ai/strategies/translate",
        json={"userInput": "RSI 전략", "unexpected": True},
    )

    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_REQUEST"


@pytest.mark.parametrize(
    ("error", "status_code", "code"),
    [
        (LLMTimeoutError(), 504, "LLM_TIMEOUT"),
        (LLMUpstreamError(), 502, "LLM_UPSTREAM_ERROR"),
        (LLMCreditLimitError(), 429, "LLM_CREDIT_LIMIT_EXCEEDED"),
        (
            DSLValidationError("invalid", attempts=3, last_errors=["safe error"]),
            422,
            "DSL_VALIDATION_FAILED",
        ),
    ],
)
def test_translate_endpoint_uses_domain_error_contract(monkeypatch, error, status_code, code):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    def raise_error(*_):
        raise error

    app.dependency_overrides[get_translator] = lambda: raise_error
    response = TestClient(app, raise_server_exceptions=False).post(
        "/api/v1/ai/strategies/translate", json={"userInput": "RSI 전략"}
    )

    assert response.status_code == status_code
    assert response.json()["code"] == code


def test_translate_endpoint_short_circuits_without_llm_call(monkeypatch):
    app = create_app(monkeypatch)
    from app.services import translator

    calls = 0

    def unexpected_call(**_):
        nonlocal calls
        calls += 1
        return "", None

    translator._llm_call_times.clear()
    monkeypatch.setattr(translator, "complete", unexpected_call)
    response = TestClient(app).post(
        "/api/v1/ai/strategies/translate", json={"userInput": "안녕하세요"}
    )

    assert response.status_code == 200
    assert response.json()["meta"]["attempts"] == 0
    assert calls == 0


def test_translate_endpoint_returns_retry_detail_for_validation_exhaustion(monkeypatch):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    def raise_error(*_):
        raise DSLValidationError("invalid", attempts=3, last_errors=["safe error"])

    app.dependency_overrides[get_translator] = lambda: raise_error
    response = TestClient(app, raise_server_exceptions=False).post(
        "/api/v1/ai/strategies/translate", json={"userInput": "RSI 전략"}
    )

    assert response.status_code == 422
    assert response.json()["detail"] == {"attempts": 3, "lastErrors": ["safe error"]}


def test_translate_endpoint_does_not_expose_upstream_error_text(monkeypatch):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    def raise_error(*_):
        raise LLMUpstreamError("test-secret-key")

    app.dependency_overrides[get_translator] = lambda: raise_error
    response = TestClient(app, raise_server_exceptions=False).post(
        "/api/v1/ai/strategies/translate", json={"userInput": "RSI 전략"}
    )

    assert response.status_code == 502
    assert "test-secret-key" not in response.text
