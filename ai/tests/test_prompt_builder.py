import builtins

import pytest


def test_loader_caches_templates_after_startup_read(tmp_path, monkeypatch):
    prompts_directory = tmp_path / "prompts"
    prompts_directory.mkdir()
    template_path = prompts_directory / "system.txt"
    template_path.write_text("You are a strategy assistant.", encoding="utf-8")

    read_count = 0
    original_open = builtins.open

    def counting_open(*args, **kwargs):
        nonlocal read_count
        if args[0] == template_path and args[1] == "r":
            read_count += 1
        return original_open(*args, **kwargs)

    monkeypatch.setattr(builtins, "open", counting_open)

    from app.services.prompt_builder import PromptLoader

    loader = PromptLoader(prompts_directory)

    assert loader.get("system") == "You are a strategy assistant."
    assert loader.get("system") == "You are a strategy assistant."
    assert read_count == 1


def test_loader_fails_when_no_prompt_file_exists(tmp_path):
    from app.services.prompt_builder import PromptLoader

    with pytest.raises(FileNotFoundError, match="No prompt files found"):
        PromptLoader(tmp_path)


def test_prompt_loader_state_is_owned_by_the_service(monkeypatch, tmp_path):
    prompts_directory = tmp_path / "prompts"
    prompts_directory.mkdir()
    (prompts_directory / "system.txt").write_text("system prompt", encoding="utf-8")

    from app.services import prompt_builder

    monkeypatch.setattr(prompt_builder, "PROMPTS_DIRECTORY", prompts_directory)
    monkeypatch.setattr(prompt_builder, "prompt_loader", None)

    prompt_builder.load_prompt_templates()

    assert prompt_builder.prompts_loaded() is True
    assert prompt_builder.get_prompt_template("system") == "system prompt"


def test_render_template_replaces_double_brace_placeholders():
    from app.services.prompt_builder import render_template

    rendered = render_template(
        "Schema: {{dsl_schema}}\nRequest: {{user_input}}",
        {"dsl_schema": "{\"type\": \"object\"}", "user_input": "RSI 전략"},
    )

    assert rendered == 'Schema: {"type": "object"}\nRequest: RSI 전략'


def test_render_template_fails_when_a_placeholder_remains():
    from app.services.prompt_builder import render_template

    with pytest.raises(ValueError, match="Unreplaced prompt placeholder"):
        render_template("Schema: {{dsl_schema}}\nInput: {{user_input}}", {"dsl_schema": "{}"})
