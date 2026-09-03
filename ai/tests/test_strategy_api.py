import importlib
import sys

from fastapi.testclient import TestClient

from app.core.schema import Strategy, TranslateMeta
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
