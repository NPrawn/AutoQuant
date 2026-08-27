import importlib
import logging
import sys
from uuid import UUID

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


def test_request_id_header_is_preserved_and_logged(monkeypatch, caplog):
    request_id = "spring-request-123"

    with caplog.at_level(logging.INFO):
        response = create_client(monkeypatch).get(
            "/health", headers={"X-Request-ID": request_id}
        )

    assert response.headers["X-Request-ID"] == request_id
    assert any(
        record.message == "request_completed"
        and record.request_id == request_id
        for record in caplog.records
    )


def test_request_id_header_is_generated_when_absent(monkeypatch):
    response = create_client(monkeypatch).get("/health")

    assert UUID(response.headers["X-Request-ID"])


def test_ready_reports_unavailable_before_prompt_files_exist(monkeypatch):
    response = create_client(monkeypatch).get("/health/ready")

    assert response.status_code == 503


def test_ready_reports_available_when_prompt_files_exist(monkeypatch, tmp_path):
    client = create_client(monkeypatch)
    monkeypatch.setattr("app.api.v1.health.PROMPTS_DIRECTORY", tmp_path)
    (tmp_path / "system.txt").write_text("system prompt", encoding="utf-8")

    response = client.get("/health/ready")

    assert response.status_code == 200
