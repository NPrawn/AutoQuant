"""Cleanup, parse, and schema-validate raw LLM responses."""

import json
from dataclasses import dataclass
from typing import Any, Generic, TypeVar

from pydantic import BaseModel, ValidationError


ModelT = TypeVar("ModelT", bound=BaseModel)


@dataclass(frozen=True)
class ValidationResult(Generic[ModelT]):
    value: ModelT | None
    errors: list[dict[str, Any]]


def extract_json_body(response_text: str) -> str:
    """Remove an outer code fence and keep the outermost JSON object text."""
    cleaned = _remove_outer_code_fence(response_text)
    opening_brace = cleaned.find("{")
    closing_brace = cleaned.rfind("}")

    if opening_brace == -1:
        return cleaned
    if closing_brace < opening_brace:
        return cleaned[opening_brace:]
    return cleaned[opening_brace : closing_brace + 1]


def validate_llm_response(
    response_text: str, schema_type: type[ModelT]
) -> ValidationResult[ModelT]:
    """Return either a validated model or recoverable parsing/validation errors."""
    try:
        payload = json.loads(extract_json_body(response_text))
    except json.JSONDecodeError as error:
        return ValidationResult(
            value=None,
            errors=[
                {
                    "loc": ["response"],
                    "msg": error.msg,
                    "type": "json_invalid",
                }
            ],
        )

    try:
        return ValidationResult(value=schema_type.model_validate(payload), errors=[])
    except ValidationError as error:
        return ValidationResult(value=None, errors=error.errors())


def _remove_outer_code_fence(response_text: str) -> str:
    stripped = response_text.strip()
    if not stripped.startswith("```"):
        return response_text

    first_newline = stripped.find("\n")
    if first_newline == -1:
        return ""

    fenced_body = stripped[first_newline + 1 :]
    if fenced_body.rstrip().endswith("```"):
        return fenced_body.rstrip()[:-3].rstrip()
    return fenced_body
