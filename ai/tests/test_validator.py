import pytest


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
