import pytest

from app.core.errors import LLMCircuitOpenError
from app.services import circuit_breaker as circuit_breaker_module
from app.services.circuit_breaker import CircuitBreaker


def open_circuit(breaker: CircuitBreaker, failures: int = 5) -> None:
    for _ in range(failures):
        breaker.record_failure()


def test_a_closed_circuit_lets_calls_through():
    breaker = CircuitBreaker()

    breaker.before_call()


def test_failures_below_the_threshold_keep_the_circuit_closed():
    breaker = CircuitBreaker(failure_threshold=5)

    open_circuit(breaker, failures=4)

    breaker.before_call()


def test_the_circuit_opens_on_the_threshold_failure():
    breaker = CircuitBreaker(failure_threshold=5)

    open_circuit(breaker)

    with pytest.raises(LLMCircuitOpenError):
        breaker.before_call()


def test_a_success_clears_the_failures_counted_so_far():
    breaker = CircuitBreaker(failure_threshold=5)

    open_circuit(breaker, failures=4)
    breaker.record_success()
    open_circuit(breaker, failures=4)

    breaker.before_call()


def test_the_circuit_lets_one_call_through_after_the_open_period(monkeypatch):
    clock = iter([0.0, 31.0])
    monkeypatch.setattr(circuit_breaker_module, "perf_counter", lambda: next(clock))
    breaker = CircuitBreaker(failure_threshold=1, open_seconds=30.0)

    breaker.record_failure()

    breaker.before_call()


def test_a_success_in_half_open_closes_the_circuit(monkeypatch):
    clock = iter([0.0, 31.0])
    monkeypatch.setattr(circuit_breaker_module, "perf_counter", lambda: next(clock))
    breaker = CircuitBreaker(failure_threshold=1, open_seconds=30.0)
    breaker.record_failure()
    breaker.before_call()

    breaker.record_success()

    breaker.before_call()


def test_a_failure_in_half_open_opens_the_circuit_again(monkeypatch):
    clock = iter([0.0, 31.0, 31.0, 31.5])
    monkeypatch.setattr(circuit_breaker_module, "perf_counter", lambda: next(clock))
    breaker = CircuitBreaker(failure_threshold=1, open_seconds=30.0)
    breaker.record_failure()
    breaker.before_call()

    breaker.record_failure()

    with pytest.raises(LLMCircuitOpenError):
        breaker.before_call()


def test_the_open_error_carries_the_shared_error_contract():
    error = LLMCircuitOpenError()

    assert error.status_code == 503
    assert error.code == "LLM_CIRCUIT_OPEN"
    assert str(error)


def test_the_module_ships_one_shared_breaker_with_the_agreed_limits():
    assert circuit_breaker_module.CONSECUTIVE_FAILURES_TO_OPEN == 5
    assert circuit_breaker_module.OPEN_SECONDS == 30.0
    assert isinstance(circuit_breaker_module.llm_circuit, CircuitBreaker)
