import importlib
import logging
import sys
from types import SimpleNamespace

import httpx
import openai
import pytest

from app.core.errors import LLMTimeoutError, LLMUpstreamError

REQUIRED_ENVIRONMENT = {
    "LLM_BASE_URL": "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
    "LLM_API_KEY": "test-secret-key",
    "LLM_MODEL": "gpt-5.4-mini",
    "APP_ENV": "test",
}


class FakeCompletions:
    def create(self, **kwargs):
        assert kwargs["model"] == "gpt-5.4-mini"
        assert kwargs["messages"] == [{"role": "user", "content": "전략을 만들어 줘"}]
        assert kwargs["timeout"] == 12.5
        return SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content="{\"ok\": true}"))],
            usage=SimpleNamespace(total_tokens=34),
        )


class FakeOpenAI:
    def __init__(self, *, api_key, base_url, max_retries):
        assert api_key == "test-secret-key"
        assert base_url == "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
        assert max_retries == 0
        self.chat = SimpleNamespace(completions=FakeCompletions())


def raising_openai(error):
    class RaisingOpenAI:
        def __init__(self, **kwargs):
            self.chat = SimpleNamespace(
                completions=SimpleNamespace(create=lambda **kwargs: (_ for _ in ()).throw(error))
            )

    return RaisingOpenAI


REQUEST = httpx.Request("POST", "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions")


@pytest.mark.parametrize(
    ("provider_error", "expected_error"),
    [
        (openai.APITimeoutError(request=REQUEST), LLMTimeoutError),
        (
            openai.APIConnectionError(
                message="gateway connection detail", request=REQUEST
            ),
            LLMUpstreamError,
        ),
        (
            openai.RateLimitError(
                "gateway response detail",
                response=httpx.Response(429, request=REQUEST),
                body={"error": "sensitive upstream body"},
            ),
            LLMUpstreamError,
        ),
        (
            openai.InternalServerError(
                "gateway response detail",
                response=httpx.Response(503, request=REQUEST),
                body={"error": "sensitive upstream body"},
            ),
            LLMUpstreamError,
        ),
    ],
)
def test_complete_translates_provider_errors_without_exposing_details(
    monkeypatch, caplog, provider_error, expected_error
):
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)
    llm_client = import_llm_client()
    monkeypatch.setattr(llm_client, "OpenAI", raising_openai(provider_error))

    with caplog.at_level(logging.INFO, logger="app"):
        with pytest.raises(expected_error) as raised:
            llm_client.complete(
                messages=[{"role": "user", "content": "전략을 만들어 줘"}],
                timeout_seconds=12.5,
            )

    assert str(raised.value) == expected_error.default_message
    assert "gateway" not in str(raised.value)
    assert "sensitive" not in str(raised.value)
    record = next(record for record in caplog.records if record.msg == "llm_call")
    assert record.success is False
    assert record.tokens is None


def import_llm_client():
    sys.modules.pop("app.core.config", None)
    sys.modules.pop("app.services.llm_client", None)
    return importlib.import_module("app.services.llm_client")


def test_module_import_does_not_require_llm_settings(monkeypatch):
    for name in REQUIRED_ENVIRONMENT:
        monkeypatch.delenv(name, raising=False)

    assert import_llm_client().complete is not None


def test_complete_returns_text_tokens_and_structured_call_log(monkeypatch, caplog):
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)
    llm_client = import_llm_client()
    monkeypatch.setattr(llm_client, "OpenAI", FakeOpenAI)

    with caplog.at_level(logging.INFO, logger="app"):
        result = llm_client.complete(
            messages=[{"role": "user", "content": "전략을 만들어 줘"}],
            timeout_seconds=12.5,
        )

    assert result == ("{\"ok\": true}", 34)
    record = next(record for record in caplog.records if record.msg == "llm_call")
    assert record.attempt == 1
    assert record.tokens == 34
    assert record.success is True
    assert record.duration_ms >= 0
