import importlib
import sys
from types import SimpleNamespace

from fastapi import Depends
from fastapi.testclient import TestClient


def create_app(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test-secret-key")
    monkeypatch.setenv("LLM_MODEL", "gpt-test")
    monkeypatch.setenv("APP_ENV", "test")
    for module_name in ("app.main", "app.api.deps", "app.core.config"):
        sys.modules.pop(module_name, None)
    return importlib.import_module("app.main").app


def test_translator_dependency_can_be_overridden(monkeypatch):
    app = create_app(monkeypatch)
    from app.api.deps import get_translator

    def fake_translator(user_input, current_strategy=None):
        return {"userInput": user_input, "hasCurrentStrategy": current_strategy is not None}

    @app.get("/__test/translator-dependency")
    def route(translator=Depends(get_translator)):
        return translator("전략 생성")

    app.dependency_overrides[get_translator] = lambda: fake_translator
    response = TestClient(app).get("/__test/translator-dependency")

    assert response.status_code == 200
    assert response.json() == {"userInput": "전략 생성", "hasCurrentStrategy": False}


def test_settings_dependency_can_be_overridden(monkeypatch):
    app = create_app(monkeypatch)
    from app.api.deps import get_settings

    @app.get("/__test/settings-dependency")
    def route(settings=Depends(get_settings)):
        return {"model": settings.llm_model}

    app.dependency_overrides[get_settings] = lambda: SimpleNamespace(llm_model="injected-model")
    response = TestClient(app).get("/__test/settings-dependency")

    assert response.status_code == 200
    assert response.json() == {"model": "injected-model"}
