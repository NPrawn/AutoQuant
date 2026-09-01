"""FastAPI application entry point."""

from contextlib import asynccontextmanager
from time import perf_counter
from uuid import uuid4

from fastapi import FastAPI, Request

from app.api.error_handlers import register_exception_handlers
from app.api.v1.health import router as health_router
from app.core.config import settings
from app.core.logging import (
    configure_logging,
    log_request_completed,
    reset_request_id,
    set_request_id,
)
from app.services.prompt_builder import load_prompt_templates


@asynccontextmanager
async def lifespan(_: FastAPI):
    load_prompt_templates()
    yield


configure_logging(settings.log_level)
app = FastAPI(lifespan=lifespan)
register_exception_handlers(app)
app.include_router(health_router)


@app.middleware("http")
async def add_request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID") or str(uuid4())
    request.state.request_id = request_id
    context_token = set_request_id(request_id)
    started_at = perf_counter()
    response = None

    # TODO: Add Spring-to-AI authentication here after the backend contract is set.
    try:
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response
    finally:
        log_request_completed(
            method=request.method,
            path=request.url.path,
            status_code=response.status_code if response is not None else 500,
            duration_ms=(perf_counter() - started_at) * 1000,
        )
        reset_request_id(context_token)
