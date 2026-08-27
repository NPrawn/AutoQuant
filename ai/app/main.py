"""FastAPI application entry point."""

from fastapi import FastAPI

from app.api.error_handlers import register_exception_handlers
from app.api.v1.health import router as health_router


app = FastAPI()
register_exception_handlers(app)
app.include_router(health_router)
