"""API exception handlers and the shared error response contract."""

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.core.errors import DomainError


logger = logging.getLogger(__name__)


def error_response(
    *,
    status_code: int,
    code: str,
    message: str,
    detail: dict[str, Any] | None = None,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"code": code, "message": message, "detail": detail},
    )


async def handle_domain_error(_: Request, error: DomainError) -> JSONResponse:
    return error_response(
        status_code=error.status_code,
        code=error.code,
        message=str(error),
    )


async def handle_unexpected_error(_: Request, error: Exception) -> JSONResponse:
    logger.exception("Unhandled API exception", exc_info=error)
    return error_response(
        status_code=500,
        code="INTERNAL_ERROR",
        message="Internal server error",
    )


async def handle_request_validation_error(
    _: Request, error: RequestValidationError
) -> JSONResponse:
    detail = {
        "fields": [
            {
                "path": ".".join(str(part) for part in item["loc"]),
                "reason": item["msg"],
            }
            for item in error.errors()
        ]
    }
    return error_response(
        status_code=400,
        code="INVALID_REQUEST",
        message="Request validation failed",
        detail=detail,
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(DomainError, handle_domain_error)
    app.add_exception_handler(RequestValidationError, handle_request_validation_error)
    app.add_exception_handler(Exception, handle_unexpected_error)
