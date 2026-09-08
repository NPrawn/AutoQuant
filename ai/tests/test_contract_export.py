"""커밋된 계약 산출물이 지금 코드에서 나오는 것과 같은지 본다.

스키마를 고치고 산출물을 안 뽑으면 여기서 빨간불이 난다. "갱신하는 것을 잊지 말자"는
규칙은 잊히고 실패하는 테스트는 안 잊힌다. 이 파일이 산출물과 코드가 갈라지지 않게
하는 유일한 장치다.
"""

import json
from pathlib import Path

import pytest

from scripts.export_contract import (
    CONTRACT_DIRECTORY,
    EXAMPLES_FILE,
    EXAMPLES_GENERATION_KEY,
    GENERATION_KEY,
    OPENAPI_FILE,
    SCHEMA_GENERATION_KEY,
    STRATEGY_SCHEMA_FILE,
    build_examples,
    build_openapi,
    build_strategy_schema,
    export,
    generation_stamp,
)


CONTRACTS = [
    (OPENAPI_FILE, GENERATION_KEY, build_openapi),
    (STRATEGY_SCHEMA_FILE, SCHEMA_GENERATION_KEY, build_strategy_schema),
    (EXAMPLES_FILE, EXAMPLES_GENERATION_KEY, build_examples),
]


def committed(filename: str, generation_key: str) -> dict:
    """커밋된 산출물에서 생성 정보를 뺀 본문. 커밋 해시는 돌릴 때마다 달라 비교 대상이 아니다."""
    document = json.loads((CONTRACT_DIRECTORY / filename).read_text(encoding="utf-8"))
    document.pop(generation_key, None)
    return document


@pytest.mark.parametrize(("filename", "generation_key", "build"), CONTRACTS)
def test_the_committed_contract_matches_what_the_code_produces_now(filename, generation_key, build):
    assert committed(filename, generation_key) == build(), (
        f"{filename} 이(가) 코드와 어긋납니다. python scripts/export_contract.py 를 다시 돌리고 커밋하세요."
    )


@pytest.mark.parametrize(("filename", "generation_key", "_build"), CONTRACTS)
def test_every_contract_carries_its_generation_commit_and_status(filename, generation_key, _build):
    document = json.loads((CONTRACT_DIRECTORY / filename).read_text(encoding="utf-8"))
    stamp = document[generation_key]
    if isinstance(stamp, str):
        stamp = json.loads(stamp)

    assert stamp["commit"]
    assert "제안" in stamp["status"]
    assert stamp["generatedBy"].endswith("export_contract.py")


def test_export_writes_every_contract(tmp_path: Path):
    written = export(tmp_path)

    assert {path.name for path in written} == {OPENAPI_FILE, STRATEGY_SCHEMA_FILE, EXAMPLES_FILE}
    assert all(path.read_text(encoding="utf-8").endswith("\n") for path in written)


def test_the_examples_cover_the_paths_a_caller_would_otherwise_miss():
    examples = build_examples()["examples"]

    responses = [example["response"] for example in examples]
    assert len(examples) == 5
    # 짧은 경로. 오류가 아니라 200 이고 strategy 가 null 이다.
    assert any(
        response.get("strategy") is None and response["meta"]["attempts"] == 0
        for response in responses
    )
    # 편집 결과. changes 가 채워진다.
    assert any(response.get("meta", {}).get("changes") for response in responses)
    # 오류 응답. 세 필드를 가진다.
    assert any(set(response) == {"code", "message", "detail"} for response in responses)


def test_the_stamp_records_the_commit_it_was_generated_from():
    stamp = generation_stamp()

    assert stamp["commit"]
    assert stamp["generatedBy"] == "scripts/export_contract.py"
