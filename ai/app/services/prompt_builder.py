"""Prompt template loading and placeholder replacement."""

from collections.abc import Mapping
from pathlib import Path
import re


_PLACEHOLDER_PATTERN = re.compile(r"\{\{[^{}]+\}\}")


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


def render_template(template: str, replacements: Mapping[str, str]) -> str:
    """Replace double-brace placeholders and reject unresolved ones."""
    rendered = template
    for name, value in replacements.items():
        rendered = rendered.replace(f"{{{{{name}}}}}", value)

    if placeholder := _PLACEHOLDER_PATTERN.search(rendered):
        raise ValueError(f"Unreplaced prompt placeholder: {placeholder.group()}")
    return rendered
