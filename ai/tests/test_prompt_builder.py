import builtins

import pytest

from app.core.schema import FactorType, Strategy


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


def test_build_messages_injects_schema_and_uses_create_template(monkeypatch):
    from app.services import prompt_builder

    monkeypatch.setattr(
        prompt_builder,
        "get_prompt_template",
        lambda name: {"system": "Schema: {{dsl_schema}}", "create": "Input: {{user_input}}"}[name],
    )

    messages = prompt_builder.build_messages(user_input="RSI 전략")

    assert messages[0]["role"] == "system"
    assert FactorType.RSI.value in messages[0]["content"]
    assert messages[1]["content"] == "Input: RSI 전략"


def test_system_prompt_includes_factor_scales_defaults_and_no_timeframe_conversion(monkeypatch):
    from app.services import prompt_builder

    monkeypatch.setattr(
        prompt_builder,
        "prompt_loader",
        prompt_builder.PromptLoader(prompt_builder.PROMPTS_DIRECTORY),
    )
    system_prompt = prompt_builder.build_messages("20% 넘게 빠진 코인 제외")[0]["content"]

    assert "DRAWDOWN" in system_prompt
    assert "-20" in system_prompt
    assert "VOLUME_RATIO" in system_prompt
    assert "1.5" in system_prompt
    assert "RSI 14" in system_prompt
    assert "timeframe conversion" not in system_prompt.lower()


def test_build_messages_uses_edit_template_when_current_strategy_exists(monkeypatch, valid_strategy_payload):
    from app.services import prompt_builder

    monkeypatch.setattr(
        prompt_builder,
        "get_prompt_template",
        lambda name: {"system": "{{dsl_schema}}", "edit": "{{current_strategy}} {{user_input}}"}[name],
    )

    messages = prompt_builder.build_messages("기간 변경", Strategy.model_validate(valid_strategy_payload))

    assert messages[1]["content"].endswith("기간 변경")


RETRY_TEMPLATES = {
    "system": "{{dsl_schema}}",
    "create": "{{user_input}}",
    "edit": "{{current_strategy}}|{{user_input}}",
    "retry": "{{previous_output}}|{{validation_errors}}",
}


def test_build_retry_messages_uses_raw_text_only_for_json_format_errors(monkeypatch):
    from app.services import prompt_builder

    monkeypatch.setattr(prompt_builder, "get_prompt_template", lambda name: RETRY_TEMPLATES[name])

    messages = prompt_builder.build_retry_messages("x", "설명 {", [{"type": "json_invalid", "loc": ["response"], "msg": "bad"}])

    assert "설명 {" in messages[-1]["content"]


def test_build_retry_messages_keeps_the_current_strategy_on_the_edit_path(monkeypatch, valid_strategy_payload):
    from app.core.schema import Strategy
    from app.services import prompt_builder

    monkeypatch.setattr(prompt_builder, "get_prompt_template", lambda name: RETRY_TEMPLATES[name])
    current_strategy = Strategy.model_validate(valid_strategy_payload)

    messages = prompt_builder.build_retry_messages(
        "기간 변경", "{}", [{"type": "missing", "loc": ["ranking"], "msg": "bad"}], current_strategy
    )

    assert current_strategy.model_dump_json() in messages[1]["content"]
    assert "기간 변경" in messages[1]["content"]
