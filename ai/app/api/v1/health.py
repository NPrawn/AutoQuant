"""Process and readiness health endpoints."""

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.services.prompt_builder import prompts_loaded


router = APIRouter()


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/health/ready")
def readiness() -> JSONResponse:
    if not prompts_loaded():
        return JSONResponse(status_code=503, content={"status": "unavailable"})
    return JSONResponse(content={"status": "ok"})
