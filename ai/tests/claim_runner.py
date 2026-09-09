"""명제 세트의 기계 판정.

정답이 발화 문장 안에 들어 있는 문장만 모았다. "1년"이라고 적힌 요청의 정답
`period=365`는 프롬프트가 가르치는 해석이 아니라 문장을 읽으면 나오는 값이다.
그래서 사람이 라벨을 붙이지 않아도 채점되고, 골든 세트처럼 오염되지 않는다.
"""

import json
from pathlib import Path
from typing import Any


def load_claim_cases(fixtures_dir: Path) -> list[dict[str, Any]]:
    """명제 세트를 읽는다."""
    return json.loads((fixtures_dir / "claim_cases.json").read_text(encoding="utf-8"))
