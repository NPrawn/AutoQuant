"""Domain errors exposed by the AI service."""


class DomainError(Exception):
    """Base class for errors translated to HTTP responses by later handlers."""

    status_code: int
    code: str
    default_message: str

    def __str__(self) -> str:
        return super().__str__() or self.default_message


class LLMTimeoutError(DomainError):
    status_code = 504
    code = "LLM_TIMEOUT"
    default_message = "LLM request timed out"


class LLMUpstreamError(DomainError):
    status_code = 502
    code = "LLM_UPSTREAM_ERROR"
    default_message = "LLM upstream service failed"


class LLMCreditLimitError(DomainError):
    status_code = 429
    code = "LLM_CREDIT_LIMIT_EXCEEDED"
    default_message = "LLM request limit exceeded"


class DSLValidationError(DomainError):
    status_code = 422
    code = "DSL_VALIDATION_FAILED"
    default_message = "DSL validation failed"

    def __init__(
        self,
        message: str | None = None,
        *,
        attempts: int | None = None,
        last_errors: list[str] | None = None,
    ) -> None:
        super().__init__(message or self.default_message)
        self.attempts = attempts
        self.last_errors = last_errors or []


class PromptTooLongError(DomainError):
    status_code = 400
    code = "INVALID_REQUEST"
    default_message = "Prompt is too long"
