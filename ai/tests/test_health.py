import importlib
import sys

from fastapi.testclient import TestClient


def create_client(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://gms.ssafy.io/gmsapi/api.openai.com/v1")
    monkeypatch.setenv("LLM_API_KEY", "test-secret-key")
    monkeypatch.setenv("LLM_MODEL", "gpt-5.4-mini")
    monkeypatch.setenv("APP_ENV", "test")
    for module_name in ("app.main", "app.api.v1.health", "app.core.config"):
        sys.modules.pop(module_name, None)
    return TestClient(importlib.import_module("app.main").app)


def test_health_reports_process_liveness(monkeypatch):
    response = create_client(monkeypatch).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_ready_reports_unavailable_before_prompt_files_exist(monkeypatch):
    response = create_client(monkeypatch).get("/health/ready")

    assert response.status_code == 503
