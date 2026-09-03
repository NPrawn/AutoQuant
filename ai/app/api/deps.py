"""FastAPI dependencies for strategy translation endpoints."""

from collections.abc import Callable

from app.core.config import Settings, settings
from app.core.schema import Strategy
from app.services.translator import TranslationResult, translate


Translator = Callable[[str, Strategy | None], TranslationResult]


def get_settings() -> Settings:
    """Provide the application settings for route injection."""
    return settings


def get_translator() -> Translator:
    """Provide the strategy translator for route injection."""
    return translate
