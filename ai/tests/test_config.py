import importlib
import logging
import sys

import pytest
from pydantic import ValidationError


REQUIRED_ENVIRONMENT = {
    "LLM_BASE_URL": "https://gms.ssafy.io/gmsapi/api.openai.com/v1",
    "LLM_API_KEY": "test-secret-key",
    "LLM_MODEL": "gpt-5.4-mini",
    "APP_ENV": "test",
}


def import_config_module():
    sys.modules.pop("app.core.config", None)
    return importlib.import_module("app.core.config")


def test_import_fails_when_required_environment_variable_is_missing(monkeypatch):
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)
    monkeypatch.delenv("LLM_API_KEY")

    with pytest.raises(ValidationError):
        import_config_module()


def test_settings_masks_api_key_and_applies_default_values(monkeypatch):
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)

    config = import_config_module()

    assert config.settings.llm_timeout_seconds == 30
    assert config.settings.max_retries == 2
    assert config.settings.log_level == "INFO"
    assert "test-secret-key" not in repr(config.settings)


def test_configure_logging_uses_log_level_from_environment(monkeypatch):
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)
    monkeypatch.setenv("LOG_LEVEL", "DEBUG")
    config = import_config_module()

    from app.core.logging import configure_logging

    root_logger = logging.getLogger()
    previous_level = root_logger.level
    try:
        configure_logging(config.settings.log_level)

        assert root_logger.level == logging.DEBUG
    finally:
        root_logger.setLevel(previous_level)
