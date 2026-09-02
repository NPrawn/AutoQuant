import pytest

from app.core.schema import Strategy


@pytest.mark.parametrize(
    ("response_text", "expected"),
    [
        ('{"strategy": "ok"}', '{"strategy": "ok"}'),
        ('```json\n{"strategy": "ok"}\n```', '{"strategy": "ok"}'),
        (
            '네, 다음과 같습니다:\n{"strategy": "ok"}\n추가 안내 문장입니다.',
            '{"strategy": "ok"}',
        ),
    ],
)
def test_extract_json_body_keeps_only_the_json_object(response_text, expected):
    from app.services.validator import extract_json_body

    assert extract_json_body(response_text) == expected


@pytest.mark.parametrize("response_text", ["", '{"strategy": "unfinished"'])
def test_extract_json_body_does_not_raise_for_empty_or_unclosed_input(response_text):
    from app.services.validator import extract_json_body

    assert extract_json_body(response_text) == response_text


def test_validate_llm_response_returns_strategy_for_cleaned_json(valid_strategy_payload):
    from app.services.validator import validate_llm_response

    result = validate_llm_response(
        f"```json\n{Strategy.model_validate(valid_strategy_payload).model_dump_json()}\n```",
        Strategy,
    )

    assert result.value == Strategy.model_validate(valid_strategy_payload)
    assert result.errors == []


@pytest.mark.parametrize("response_text", ["", "{", "[]", '"not a strategy"'])
def test_validate_llm_response_returns_errors_without_raising(response_text):
    from app.services.validator import validate_llm_response

    result = validate_llm_response(response_text, Strategy)

    assert result.value is None
    assert result.errors


def test_format_retry_errors_includes_path_and_allowed_values():
    from app.services.validator import format_retry_errors

    message = format_retry_errors(
        [
            {
                "loc": ("ranking", "factor"),
                "msg": "Input should be 'RETURN' or 'RSI'",
                "ctx": {"expected": "'RETURN' or 'RSI'"},
            }
        ]
    )

    assert "ranking.factor" in message
    assert "RETURN" in message
    assert "RSI" in message


def test_format_user_errors_uses_korean_summary_without_raw_validation_text():
    from app.services.validator import format_user_errors

    message = format_user_errors(
        [{"loc": ("ranking", "factor"), "msg": "Input should be 'RETURN'"}]
    )

    assert "전략" in message
    assert "Input should" not in message
