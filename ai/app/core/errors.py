"""Domain errors exposed by the AI service."""


class DomainError(Exception):
    """Base class for errors translated to HTTP responses by later handlers."""

    status_code: int
    code: str


class LLMTimeoutError(DomainError):
    status_code = 504
    code = "LLM_TIMEOUT"


class LLMUpstreamError(DomainError):
    status_code = 502
    code = "LLM_UPSTREAM_ERROR"


class DSLValidationError(DomainError):
    status_code = 422
    code = "DSL_VALIDATION_FAILED"


class PromptTooLongError(DomainError):
    status_code = 400
    code = "INVALID_REQUEST"
