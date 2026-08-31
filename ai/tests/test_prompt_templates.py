from pathlib import Path
import re

from tests.test_health import create_client


PROMPTS_DIRECTORY = Path(__file__).parents[1] / "app" / "prompts"
EXPECTED_TEMPLATES = {"system", "create", "edit", "retry"}
ALLOWED_PLACEHOLDERS = {
    "dsl_schema",
    "user_input",
    "current_strategy",
    "previous_output",
    "validation_errors",
}


def placeholders(template: str) -> set[str]:
    return set(re.findall(r"\{\{([a-z_]+)\}\}", template))


def test_prompt_templates_use_only_the_approved_placeholders():
    prompt_paths = sorted(PROMPTS_DIRECTORY.glob("*.txt"))

    assert {prompt_path.stem for prompt_path in prompt_paths} == EXPECTED_TEMPLATES
    assert all(
        placeholders(prompt_path.read_text(encoding="utf-8")) <= ALLOWED_PLACEHOLDERS
        for prompt_path in prompt_paths
    )


def test_prompt_templates_cover_each_prompt_role():
    templates = {
        name: (PROMPTS_DIRECTORY / f"{name}.txt").read_text(encoding="utf-8")
        for name in EXPECTED_TEMPLATES
    }

    assert "{{dsl_schema}}" in templates["system"]
    assert "{{user_input}}" in templates["create"]
    assert "{{current_strategy}}" in templates["edit"]
    assert "{{previous_output}}" in templates["retry"]
    assert "{{validation_errors}}" in templates["retry"]


def test_ready_is_available_after_prompt_templates_are_loaded(monkeypatch):
    with create_client(monkeypatch) as client:
        response = client.get("/health/ready")

    assert response.status_code == 200
