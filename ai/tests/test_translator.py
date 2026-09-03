from app.core.schema import StrategyEnvelope


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
    assert result.value.strategy.timeframe.value == "4H"


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
