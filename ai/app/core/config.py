"""Application settings loaded from the environment."""

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the AI strategy translation service."""

    llm_base_url: str
    llm_api_key: SecretStr
    llm_model: str
    llm_timeout_seconds: int = 30
    max_retries: int = 2
    log_level: str = "INFO"
    app_env: str

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
