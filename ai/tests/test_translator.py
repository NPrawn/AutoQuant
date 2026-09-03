from app.core.schema import StrategyEnvelope
import pytest


def test_translate_returns_validated_envelope(monkeypatch, valid_strategy_payload):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator

    monkeypatch.setattr(translator, "build_messages", lambda *_: [{"role": "user", "content": "x"}])
    monkeypatch.setattr(translator, "complete", lambda **_: (StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json(), None))
    result = translator.translate("전략 생성")

    assert result.value is not None
    assert result.value.timeframe.value == "4H"
    assert result.meta.attempts == 1


def test_translate_retries_only_validation_failures(monkeypatch, valid_strategy_payload):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator

    responses = iter(["{}", StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json()])
    monkeypatch.setattr(translator, "build_messages", lambda *_: [{"role": "user", "content": "x"}])
    monkeypatch.setattr(translator, "build_retry_messages", lambda *_: [{"role": "user", "content": "retry"}])
    monkeypatch.setattr(translator, "complete", lambda **_: (next(responses), None))

    assert translator.translate("전략 생성").value is not None


def test_describe_changes_marks_new_strategy(valid_strategy_payload):
    from app.services.translator import describe_changes

    assert describe_changes(None, StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).strategy)


@pytest.mark.parametrize("error_name", ["LLMTimeoutError", "LLMUpstreamError"])
def test_translate_does_not_retry_upstream_errors(monkeypatch, error_name):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.core import errors
    from app.services import translator

    calls = 0
    def fail(**_):
        nonlocal calls
        calls += 1
        raise getattr(errors, error_name)()

    monkeypatch.setattr(translator, "build_messages", lambda *_: [])
    monkeypatch.setattr(translator, "complete", fail)

    with pytest.raises(getattr(errors, error_name)):
        translator.translate("전략 생성")
    assert calls == 1


def test_describe_changes_reports_filter_change(valid_strategy_payload):
    from app.services.translator import describe_changes

    previous = StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).strategy
    changed = previous.model_copy(deep=True)
    changed.selection.topN = 3

    assert any("selection" in item for item in describe_changes(previous, changed))


def test_translate_returns_last_errors_after_retry_exhaustion(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator

    calls = 0
    def invalid(**_):
        nonlocal calls
        calls += 1
        return "{}", None

    monkeypatch.setattr(translator, "build_messages", lambda *_: [])
    monkeypatch.setattr(translator, "build_retry_messages", lambda *_: [])
    monkeypatch.setattr(translator, "complete", invalid)

    result = translator.translate("전략 생성")
    assert result.value is None
    assert result.errors
    assert calls == 3
