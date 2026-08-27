"""FastAPI application entry point."""

from fastapi import FastAPI

from app.api.v1.health import router as health_router


app = FastAPI()
app.include_router(health_router)
