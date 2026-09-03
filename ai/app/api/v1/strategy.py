"""Strategy translation endpoint."""

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import Translator, get_translator
from app.core.schema import TranslateRequest, TranslateResponse


router = APIRouter(prefix="/api/v1/ai/strategies", tags=["strategies"])


@router.post("/translate", response_model=TranslateResponse)
def translate_strategy(
    request: TranslateRequest,
    translator: Annotated[Translator, Depends(get_translator)],
) -> TranslateResponse:
    result = translator(request.userInput, request.currentStrategy)
    return TranslateResponse(strategy=result.value, meta=result.meta)
