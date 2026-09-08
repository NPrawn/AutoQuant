"""서킷이 실제 호출 경로에서 무엇을 막고 무엇을 안 세는지 본다. 실제 API는 치지 않는다."""

import pytest

from app.core.errors import (
    DSLValidationError,
    LLMCircuitOpenError,
    LLMCreditLimitError,
    LLMTimeoutError,
    LLMUpstreamError,
)
from app.core.schema import StrategyEnvelope
from tests.test_health import create_client


@pytest.fixture
def translator(monkeypatch):
    monkeypatch.setenv("LLM_BASE_URL", "https://example.test")
    monkeypatch.setenv("LLM_API_KEY", "test")
    monkeypatch.setenv("LLM_MODEL", "test")
    monkeypatch.setenv("APP_ENV", "test")
    from app.services import translator as translator_module
    from app.services.circuit_breaker import llm_circuit

    llm_circuit.reset()
    translator_module._llm_call_times.clear()
    monkeypatch.setattr(
        translator_module, "build_messages", lambda *_: [{"role": "user", "content": "x"}]
    )
    monkeypatch.setattr(
        translator_module, "build_retry_messages", lambda *_: [{"role": "user", "content": "retry"}]
    )
    yield translator_module
    llm_circuit.reset()
    translator_module._llm_call_times.clear()


def count_calls(monkeypatch, translator_module, behaviour) -> list[int]:
    calls = [0]

    def fake_complete(**_):
        calls[0] += 1
        return behaviour(calls[0])

    monkeypatch.setattr(translator_module, "complete", fake_complete)
    return calls


def test_five_gateway_failures_open_the_circuit_and_stop_the_calls(monkeypatch, translator):
    def always_fail(_: int):
        raise LLMUpstreamError()

    calls = count_calls(monkeypatch, translator, always_fail)

    for _ in range(5):
        with pytest.raises(LLMUpstreamError):
            translator.translate("전략 생성")

    assert calls[0] == 5
    with pytest.raises(LLMCircuitOpenError):
        translator.translate("전략 생성")
    assert calls[0] == 5


def test_timeouts_count_toward_opening_the_circuit(monkeypatch, translator):
    def always_time_out(_: int):
        raise LLMTimeoutError()

    calls = count_calls(monkeypatch, translator, always_time_out)

    for _ in range(5):
        with pytest.raises(LLMTimeoutError):
            translator.translate("전략 생성")

    with pytest.raises(LLMCircuitOpenError):
        translator.translate("전략 생성")
    assert calls[0] == 5


def test_validation_failures_do_not_open_the_circuit(monkeypatch, translator, valid_strategy_payload):
    # 재시도까지 모두 형식이 깨진 응답이라 DSLValidationError 로 끝난다. 게이트웨이는 멀쩡하다.
    calls = count_calls(monkeypatch, translator, lambda _: ("{}", None))

    for _ in range(5):
        with pytest.raises(DSLValidationError):
            translator.translate("전략 생성")

    calls_before = calls[0]
    monkeypatch.setattr(
        translator,
        "complete",
        lambda **_: (
            StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json(),
            None,
        ),
    )
    assert translator.translate("전략 생성").value is not None
    assert calls_before > 0


def test_the_credit_limit_does_not_open_the_circuit(monkeypatch, translator, valid_strategy_payload):
    envelope = StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json()
    count_calls(monkeypatch, translator, lambda _: (envelope, None))
    monkeypatch.setattr(translator, "MAX_LLM_CALLS_PER_MINUTE", 1)

    assert translator.translate("전략 생성").value is not None
    for _ in range(5):
        with pytest.raises(LLMCreditLimitError):
            translator.translate("전략 생성")

    # 서킷이 열렸다면 크레딧 오류가 아니라 LLMCircuitOpenError 가 나온다.
    with pytest.raises(LLMCreditLimitError):
        translator.translate("전략 생성")


def test_the_circuit_closes_again_after_a_successful_trial(monkeypatch, translator, valid_strategy_payload):
    envelope = StrategyEnvelope(strategy=valid_strategy_payload, assumptions=[]).model_dump_json()

    def fail_then_succeed(call_count: int):
        if call_count <= 5:
            raise LLMUpstreamError()
        return (envelope, None)

    from app.services import circuit_breaker as circuit_breaker_module
    from app.services.circuit_breaker import llm_circuit

    clock = [0.0]
    monkeypatch.setattr(circuit_breaker_module, "perf_counter", lambda: clock[0])

    calls = count_calls(monkeypatch, translator, fail_then_succeed)
    for _ in range(5):
        with pytest.raises(LLMUpstreamError):
            translator.translate("전략 생성")

    # 차단 시간이 지난 것으로 보이게 시계를 앞으로 민다.
    clock[0] = circuit_breaker_module.OPEN_SECONDS + 1

    assert translator.translate("전략 생성").value is not None
    assert calls[0] == 6
    llm_circuit.before_call()


def test_the_open_circuit_answers_with_the_shared_error_contract(monkeypatch):
    with create_client(monkeypatch) as client:
        from app.services import translator as translator_module
        from app.services.circuit_breaker import llm_circuit

        translator_module._llm_call_times.clear()
        llm_circuit.reset()
        # 프롬프트 조립은 이 테스트의 대상이 아니다. 앞선 테스트가 로더 상태를 비워 두면
        # 그쪽에서 먼저 터져 서킷 계약을 못 본다.
        monkeypatch.setattr(
            translator_module, "build_messages", lambda *_: [{"role": "user", "content": "x"}]
        )
        monkeypatch.setattr(
            translator_module, "complete", lambda **_: (_ for _ in ()).throw(LLMUpstreamError())
        )
        for _ in range(5):
            client.post("/api/v1/ai/strategies/translate", json={"userInput": "5% 이상 오른 코인"})

        response = client.post(
            "/api/v1/ai/strategies/translate", json={"userInput": "5% 이상 오른 코인"}
        )
        health = client.get("/health")
        health_ready = client.get("/health/ready")
        llm_circuit.reset()

    assert response.status_code == 503
    assert set(response.json()) == {"code", "message", "detail"}
    assert response.json()["code"] == "LLM_CIRCUIT_OPEN"
    # 서킷은 LLM 호출 지점에만 걸린다. 헬스 엔드포인트는 영향을 받지 않는다.
    assert health.status_code == 200
    assert health_ready.status_code == 200
