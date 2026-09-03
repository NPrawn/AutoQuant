import pytest

from app.core.errors import (
    DSLValidationError,
    LLMTimeoutError,
    LLMUpstreamError,
    LLMCreditLimitError,
    PromptTooLongError,
)


@pytest.mark.parametrize(
    ("error_type", "status_code", "code"),
    [
        (LLMTimeoutError, 504, "LLM_TIMEOUT"),
        (LLMUpstreamError, 502, "LLM_UPSTREAM_ERROR"),
        (DSLValidationError, 422, "DSL_VALIDATION_FAILED"),
        (PromptTooLongError, 400, "INVALID_REQUEST"),
        (LLMCreditLimitError, 429, "LLM_CREDIT_LIMIT_EXCEEDED"),
    ],
)
def test_domain_errors_expose_stable_http_contract(error_type, status_code, code):
    error = error_type("request failed")

    assert error.status_code == status_code
    assert error.code == code
    assert str(error) == "request failed"


@pytest.mark.parametrize(
    "error_type",
    [LLMTimeoutError, LLMUpstreamError, DSLValidationError, PromptTooLongError, LLMCreditLimitError],
)
def test_domain_errors_use_their_default_message_when_raised_without_arguments(
    error_type,
):
    error = error_type()

    assert str(error) == error.default_message


def test_dsl_validation_error_keeps_safe_retry_context():
    error = DSLValidationError(
        "전략 형식이 올바르지 않습니다.",
        attempts=3,
        last_errors=["전략 형식이 올바르지 않습니다. 2개 항목을 확인해 주세요."],
    )

    assert error.attempts == 3
    assert error.last_errors == ["전략 형식이 올바르지 않습니다. 2개 항목을 확인해 주세요."]
