"""Raw LLM response fixtures shared by validation and orchestration tests."""

CODE_FENCED_OBJECT = '```json\n{"strategy": "ok"}\n```'
NARRATED_OBJECT = '결과입니다.\n{"strategy": "ok"}\n설명은 생략합니다.'
UNCLOSED_OBJECT = '{"strategy": '
EMPTY_RESPONSE = ""
JSON_ARRAY = "[]"
JSON_STRING = '"not a strategy"'
