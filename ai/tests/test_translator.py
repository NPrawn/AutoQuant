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
    assert result.meta.elapsedMs >= 0
    # 4H 캔들 20개(약 3.3일)로 순위를 매기고 WEEKLY 로 담는 전략이라 경고가 하나 붙는다.
    assert result.meta.warnings == [
        "순위를 20개 4H 캔들로 매기는데 WEEKLY 주기로 담습니다. 관찰 구간이 보유 기간보다 짧습니다."
    ]


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


def test_translate_short_circuits_greeting_without_calling_llm(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator

    monkeypatch.setattr(translator, "complete", lambda **_: pytest.fail("LLM must not be called"))
    result = translator.translate("안녕하세요")

    assert result.value is None
    assert result.meta.attempts == 0
    assert result.meta.assumptions


def test_translate_sends_ambiguous_strategy_request_to_llm(monkeypatch, valid_strategy_payload):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator

    calls = 0

    def complete_once(**_):
        nonlocal calls
        calls += 1
        return StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json(), None

    monkeypatch.setattr(translator, "build_messages", lambda *_: [])
    monkeypatch.setattr(translator, "complete", complete_once)
    translator.translate("돈 많이 버는 전략")

    assert calls == 1


def test_translate_blocks_llm_calls_after_per_minute_limit(monkeypatch, valid_strategy_payload):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.core.errors import LLMCreditLimitError
    from app.services import translator

    translator._llm_call_times.clear()
    monkeypatch.setattr(translator, "build_messages", lambda *_: [])
    monkeypatch.setattr(
        translator,
        "complete",
        lambda **_: (StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json(), None),
    )
    try:
        for _ in range(translator.MAX_LLM_CALLS_PER_MINUTE):
            translator.translate("모멘텀 전략")
        with pytest.raises(LLMCreditLimitError):
            translator.translate("모멘텀 전략")
    finally:
        translator._llm_call_times.clear()


def test_describe_changes_marks_new_strategy(valid_strategy_payload):
    from app.services.translator import describe_changes

    assert describe_changes(None, StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).strategy) == []


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

    from app.core.errors import DSLValidationError
    with pytest.raises(DSLValidationError) as raised:
        translator.translate("전략 생성")
    assert calls == 3
    assert raised.value.attempts == 3
    assert raised.value.last_errors == ["전략 형식이 올바르지 않습니다. 2개 항목을 확인해 주세요."]


def test_translate_passes_current_strategy_to_message_builder(monkeypatch, valid_strategy_payload):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator

    current = StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).strategy
    seen = []
    monkeypatch.setattr(translator, "build_messages", lambda *args: seen.append(args) or [])
    monkeypatch.setattr(translator, "complete", lambda **_: (StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json(), None))
    translator.translate("수정", current)
    assert seen[0][1] == current


@pytest.mark.parametrize("field", ["timeframe", "ranking", "rebalance"])
def test_describe_changes_keeps_unchanged_strategy_empty(valid_strategy_payload, field):
    from app.services.translator import describe_changes

    strategy = StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).strategy
    assert describe_changes(strategy, strategy) == []


def test_translate_keeps_the_current_strategy_across_retries(monkeypatch, valid_strategy_payload):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.core.schema import Strategy
    from app.services import translator

    current_strategy = Strategy.model_validate(valid_strategy_payload)
    responses = iter(["{}", StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json()])
    retry_arguments = []
    monkeypatch.setattr(translator, "build_messages", lambda *_: [{"role": "user", "content": "x"}])
    monkeypatch.setattr(
        translator,
        "build_retry_messages",
        lambda *arguments: retry_arguments.append(arguments) or [{"role": "user", "content": "retry"}],
    )
    monkeypatch.setattr(translator, "complete", lambda **_: (next(responses), None))

    translator.translate("기간 변경", current_strategy)

    assert retry_arguments[0][-1] == current_strategy
