"""Prompt template loading and placeholder replacement."""

from collections.abc import Mapping
from pathlib import Path
import re

from pydantic import BaseModel

from app.core.schema import strategy_envelope_json_schema
from app.services.validator import extract_json_body, format_retry_errors


_PLACEHOLDER_PATTERN = re.compile(r"\{\{[^{}]+\}\}")
PROMPTS_DIRECTORY = Path(__file__).parents[1] / "prompts"
prompt_loader: "PromptLoader | None" = None


class PromptLoader:
    """Load every prompt template once and serve it from memory."""

    def __init__(self, prompts_directory: Path):
        prompt_paths = sorted(prompts_directory.glob("*.txt"))
        if not prompt_paths:
            raise FileNotFoundError(f"No prompt files found in {prompts_directory}")

        self._templates = {
            prompt_path.stem: self._read_template(prompt_path)
            for prompt_path in prompt_paths
        }

    @staticmethod
    def _read_template(prompt_path: Path) -> str:
        with open(prompt_path, "r", encoding="utf-8") as prompt_file:
            return prompt_file.read()

    def get(self, name: str) -> str:
        """Return a cached template by its filename without extension."""
        return self._templates[name]


def load_prompt_templates() -> None:
    """Load the application prompt cache before accepting requests."""
    global prompt_loader
    prompt_loader = PromptLoader(PROMPTS_DIRECTORY)


def prompts_loaded() -> bool:
    """Return whether the application prompt cache is ready for use."""
    return prompt_loader is not None


def get_prompt_template(name: str) -> str:
    """Return a template from the application prompt cache."""
    if prompt_loader is None:
        raise RuntimeError("Prompt templates have not been loaded")
    return prompt_loader.get(name)


def render_template(template: str, replacements: Mapping[str, str]) -> str:
    """Replace double-brace placeholders and reject unresolved ones."""
    rendered = template
    for name, value in replacements.items():
        rendered = rendered.replace(f"{{{{{name}}}}}", value)

    if placeholder := _PLACEHOLDER_PATTERN.search(rendered):
        raise ValueError(f"Unreplaced prompt placeholder: {placeholder.group()}")
    return rendered


def build_messages(
    user_input: str, current_strategy: BaseModel | None = None
) -> list[dict[str, str]]:
    """Build a fresh create or edit request with the current DSL schema."""
    system_prompt = render_template(
        get_prompt_template("system"), {"dsl_schema": strategy_envelope_json_schema()}
    )
    template_name = "edit" if current_strategy is not None else "create"
    replacements = {"user_input": user_input}
    if current_strategy is not None:
        replacements["current_strategy"] = current_strategy.model_dump_json()
    return [
        {"role": "system", "content": system_prompt},
        {
            "role": "user",
            "content": render_template(get_prompt_template(template_name), replacements),
        },
    ]


def build_retry_messages(
    user_input: str,
    previous_output: str,
    errors: list[dict[str, object]],
    current_strategy: BaseModel | None = None,
) -> list[dict[str, str]]:
    """Repeat the original create or edit request and append the failed attempt."""
    previous_for_retry = (
        previous_output
        if any(error.get("type") == "json_invalid" for error in errors)
        else extract_json_body(previous_output)
    )
    retry_prompt = render_template(
        get_prompt_template("retry"),
        {
            "previous_output": previous_for_retry,
            "validation_errors": format_retry_errors(errors),
        },
    )
    return [
        *build_messages(user_input, current_strategy),
        {"role": "user", "content": retry_prompt},
    ]
