"""Process and readiness health endpoints."""

from pathlib import Path

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.services.prompt_builder import PromptLoader


router = APIRouter()
PROMPTS_DIRECTORY = Path(__file__).parents[2] / "prompts"
prompt_loader: PromptLoader | None = None


def load_prompt_templates() -> None:
    """Load prompt templates before the application accepts requests."""
    global prompt_loader
    prompt_loader = PromptLoader(PROMPTS_DIRECTORY)


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/health/ready")
def readiness() -> JSONResponse:
    if not any(PROMPTS_DIRECTORY.glob("*.txt")):
        return JSONResponse(status_code=503, content={"status": "unavailable"})
    return JSONResponse(content={"status": "ok"})
