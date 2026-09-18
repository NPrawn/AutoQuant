# 프롬프트 축 재구성 구현 계획

> **에이전트에게:** 이 계획은 `superpowers:subagent-driven-development` 또는 `superpowers:executing-plans`로 작업 단위마다 실행한다. 단계는 체크박스(`- [ ]`)로 추적한다.

**목표:** `system.txt`를 관용구 표 중심에서 팩터 축 정의 중심으로 바꾸고, 그 효과를 기계로 채점되는 명제 세트로 증명한다.

**접근:** 검증 장치를 먼저 만들고 기준선을 잰 뒤에 프롬프트를 고친다. 명제 세트는 정답이 발화 문장 안에 들어 있는 문장만 모아 사람 라벨 없이 채점한다. 채점 러너는 기존 `holdout_runner.py` 구조를 그대로 따른다.

**스택:** Python 3.11+, pytest, Pydantic v2. 새 의존성 없음.

설계 근거는 [DESIGN-PROMPT-AXES.md](DESIGN-PROMPT-AXES.md)에 있다.

## 전역 제약

- 작업 디렉터리는 `S15P21A107/ai`다. 모든 경로는 여기 기준이다.
- 의존성은 `fastapi`, `uvicorn[standard]`, `pydantic`, `pydantic-settings`, `openai>=1.60`, `pytest`, `httpx`가 전부다. 여기 없는 패키지를 추가하지 않는다.
- DSL 필드명과 허용값은 `app/core/schema.py`에만 적는다. 러너는 `FactorType`·`Timeframe`·`TIMEFRAME_MINUTES`를 임포트해 쓰고 `"RETURN"` 같은 문자열을 다시 적지 않는다.
- `os.environ`은 `app/core/config.py`에서만 읽는다.
- 모든 파일은 개행으로 끝낸다. 와일드카드 임포트를 쓰지 않는다.
- LLM을 부르는 테스트에는 `@pytest.mark.live`를 붙인다. 기본 실행에서 제외된다.
- 연속 호출 간격은 2.1초 이상이다. `MIN_CALL_INTERVAL_SECONDS`를 따른다.
- 커밋 메시지는 `<type>: <한국어 작업 내용>` 형식이고 마침표를 붙이지 않는다. Jira 키를 쓰지 않는다.

**선행 조건.** 작업 전 `git status`를 확인한다. 이전 세션에서 고친 `app/prompts/edit.txt`, `app/prompts/system.txt`, `frontend/` 세 파일이 커밋되지 않은 채 `develop`에 있다. 브랜치를 따고 그 변경을 먼저 커밋한 뒤 시작한다. 섞이면 이 계획의 측정 기준선이 무엇에 대한 기준선인지 알 수 없게 된다.

## 파일 구조

| 파일 | 책임 |
|---|---|
| `tests/fixtures/claim_cases.json` | 명제 세트 36건. 발화와 기계 채점에 필요한 기대값 |
| `tests/claim_runner.py` | 로딩과 채점. LLM을 부르지 않는 순수 함수만 |
| `tests/test_claim_runner.py` | 채점 함수의 단위 테스트 |
| `tests/test_claim_live.py` | GMS를 부르는 측정 실행기. 리포트를 `reports/`에 쓴다 |
| `tests/fixtures/holdout_inputs.md` | H-20·H-22 제거 (28건 → 26건) |
| `tests/test_holdout_runner.py` | 건수 단언 갱신 |
| `tests/test_holdout_live.py` | 건수 단언 갱신 |
| `app/prompts/system.txt` | 재구성 대상 |
| `docs/DESIGN-PROMPT-AXES.md` | 측정 결과표 추가 |

---

### Task 1: 명제 세트와 로더

**Files:**
- Create: `tests/fixtures/claim_cases.json`
- Create: `tests/claim_runner.py`
- Create: `tests/test_claim_runner.py`

**Interfaces:**
- Produces: `load_claim_cases(fixtures_dir: Path) -> list[dict[str, Any]]`

각 케이스는 `id`, `claim`, `userInput`을 갖는다. `claim`은 `"period"`, `"no-approximation"`, `"synonym"` 중 하나다. `claim`에 따라 `days`(정수), `forbiddenFactors`(문자열 배열), `expectedFactor`(문자열)가 하나씩 더 붙는다. `seen`은 이미 실패를 확인한 문장에만 `true`로 붙는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`tests/test_claim_runner.py`:

```python
from pathlib import Path

import pytest

from tests.claim_runner import load_claim_cases


FIXTURES_DIR = Path(__file__).parent / "fixtures"


def test_the_claim_set_loads_with_twelve_cases_per_claim():
    cases = load_claim_cases(FIXTURES_DIR)

    assert len(cases) == 36
    assert all(case["userInput"] for case in cases)
    assert sum(case["claim"] == "period" for case in cases) == 12
    assert sum(case["claim"] == "no-approximation" for case in cases) == 12
    assert sum(case["claim"] == "synonym" for case in cases) == 12


def test_every_case_carries_the_field_its_claim_is_scored_on():
    required = {
        "period": "days",
        "no-approximation": "forbiddenFactors",
        "synonym": "expectedFactor",
    }

    for case in load_claim_cases(FIXTURES_DIR):
        assert required[case["claim"]] in case, case["id"]


def test_the_two_cases_moved_from_the_holdout_are_marked_seen():
    seen = [case["id"] for case in load_claim_cases(FIXTURES_DIR) if case.get("seen")]

    assert sorted(seen) == ["C1-12", "C2-12"]
```

- [ ] **Step 2: 실패를 확인한다**

실행: `python -m pytest tests/test_claim_runner.py -v`
기대: `ModuleNotFoundError: No module named 'tests.claim_runner'`

- [ ] **Step 3: 픽스처를 쓴다**

`tests/fixtures/claim_cases.json`:

```json
[
  {"id": "C1-01", "claim": "period", "userInput": "1년 수익률이 플러스인 코인만 골라줘", "days": 365},
  {"id": "C1-02", "claim": "period", "userInput": "석 달 동안 오른 코인을 사고 싶어", "days": 90},
  {"id": "C1-03", "claim": "period", "userInput": "열흘 수익률이 높은 순으로 담아줘", "days": 10},
  {"id": "C1-04", "claim": "period", "userInput": "2주 동안 변동성이 낮았던 것만 골라줘", "days": 14},
  {"id": "C1-05", "claim": "period", "userInput": "100일 최고가 대비 30% 넘게 빠진 코인", "days": 100},
  {"id": "C1-06", "claim": "period", "userInput": "반년 동안 꾸준히 오른 코인을 사고 싶어", "days": 180},
  {"id": "C1-07", "claim": "period", "userInput": "사흘 거래량이 평균보다 두 배 넘는 종목", "days": 3},
  {"id": "C1-08", "claim": "period", "userInput": "45일 수익률 기준으로 순위를 매겨줘", "days": 45},
  {"id": "C1-09", "claim": "period", "userInput": "한 달 이동평균에서 5% 아래로 내려온 코인", "days": 30},
  {"id": "C1-10", "claim": "period", "userInput": "9일 RSI가 30 아래인 것만 사고 싶어", "days": 9},
  {"id": "C1-11", "claim": "period", "userInput": "250일 변동성이 가장 낮은 코인으로 골라줘", "days": 250},
  {"id": "C1-12", "claim": "period", "userInput": "1년 내내 계단식 우하향만 그리는 차트는 쳐다보지도 않습니다", "days": 365, "seen": true},

  {"id": "C2-01", "claim": "no-approximation", "userInput": "거래대금이 큰 종목만 담아줘", "forbiddenFactors": ["VOLUME_RATIO"]},
  {"id": "C2-02", "claim": "no-approximation", "userInput": "시가총액 상위권에서만 골라줘", "forbiddenFactors": ["VOLUME_RATIO"]},
  {"id": "C2-03", "claim": "no-approximation", "userInput": "유동성 좋은 메이저 코인 위주로 사고 싶어", "forbiddenFactors": ["VOLUME_RATIO"]},
  {"id": "C2-04", "claim": "no-approximation", "userInput": "하루 거래대금 1000억 넘는 것만 후보로 해줘", "forbiddenFactors": ["VOLUME_RATIO"]},
  {"id": "C2-05", "claim": "no-approximation", "userInput": "10% 손절을 걸어줘", "forbiddenFactors": []},
  {"id": "C2-06", "claim": "no-approximation", "userInput": "20% 오르면 팔아줘", "forbiddenFactors": []},
  {"id": "C2-07", "claim": "no-approximation", "userInput": "한 번 사면 세 달은 들고 있게 해줘", "forbiddenFactors": []},
  {"id": "C2-08", "claim": "no-approximation", "userInput": "비중을 절반은 비트코인에 넣어줘", "forbiddenFactors": []},
  {"id": "C2-09", "claim": "no-approximation", "userInput": "상장폐지 위험 있는 종목은 빼줘", "forbiddenFactors": []},
  {"id": "C2-10", "claim": "no-approximation", "userInput": "뉴스에 많이 나온 코인으로 골라줘", "forbiddenFactors": []},
  {"id": "C2-11", "claim": "no-approximation", "userInput": "김치 프리미엄 낀 종목은 제외해줘", "forbiddenFactors": []},
  {"id": "C2-12", "claim": "no-approximation", "userInput": "거래량 바닥이라 내가 100만 원어치만 사도 호가창 요동치고 팔고 싶을 때 못 빠져나오는 코인은 손도 안 댑니다", "forbiddenFactors": ["VOLUME_RATIO"], "seen": true},

  {"id": "C3-01", "claim": "synonym", "userInput": "거래량이 평소보다 확 늘어난 코인을 사고 싶어", "expectedFactor": "VOLUME_RATIO"},
  {"id": "C3-02", "claim": "synonym", "userInput": "손바뀜이 갑자기 활발해진 종목을 골라줘", "expectedFactor": "VOLUME_RATIO"},
  {"id": "C3-03", "claim": "synonym", "userInput": "고점에서 크게 밀렸다가 다시 올라오는 것을 사고 싶어", "expectedFactor": "DRAWDOWN"},
  {"id": "C3-04", "claim": "synonym", "userInput": "많이 눌린 자리에서 살아나는 코인을 골라줘", "expectedFactor": "DRAWDOWN"},
  {"id": "C3-05", "claim": "synonym", "userInput": "전고점을 새로 뚫은 코인을 사고 싶어", "expectedFactor": "DRAWDOWN"},
  {"id": "C3-06", "claim": "synonym", "userInput": "이동평균선 밑으로 많이 내려간 코인을 담아줘", "expectedFactor": "SMA_DISPARITY"},
  {"id": "C3-07", "claim": "synonym", "userInput": "단기선이 장기선 위로 올라온 종목을 사고 싶어", "expectedFactor": "SMA_DISPARITY"},
  {"id": "C3-08", "claim": "synonym", "userInput": "가격이 심하게 출렁이는 코인을 골라줘", "expectedFactor": "VOLATILITY"},
  {"id": "C3-09", "claim": "synonym", "userInput": "얌전하게 움직이는 코인만 담고 싶어", "expectedFactor": "VOLATILITY"},
  {"id": "C3-10", "claim": "synonym", "userInput": "요즘 계속 우상향인 코인을 사고 싶어", "expectedFactor": "RETURN"},
  {"id": "C3-11", "claim": "synonym", "userInput": "과매도 구간에 들어간 코인을 골라줘", "expectedFactor": "RSI"},
  {"id": "C3-12", "claim": "synonym", "userInput": "밴드 아래쪽까지 흘러내린 코인을 사고 싶어", "expectedFactor": "SMA_DISPARITY"}
]
```

- [ ] **Step 4: 로더를 쓴다**

`tests/claim_runner.py`:

```python
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
```

- [ ] **Step 5: 통과를 확인한다**

실행: `python -m pytest tests/test_claim_runner.py -v`
기대: 3 passed

- [ ] **Step 6: 커밋한다**

```bash
git add tests/fixtures/claim_cases.json tests/claim_runner.py tests/test_claim_runner.py
git commit -m "test: 프롬프트 일반화 측정용 명제 세트 36건 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

프롬프트를 고치기 전에 세트를 먼저 커밋하는 것이 이 계획의 전제다. 나중에 세트를 결과에 맞춰 고치면 측정이 무의미해진다.

---

### Task 2: 채점 함수

**Files:**
- Modify: `tests/claim_runner.py`
- Modify: `tests/test_claim_runner.py`

**Interfaces:**
- Consumes: `load_claim_cases` (Task 1)
- Produces:
  - `candles_for_days(days: int, timeframe: str) -> int`
  - `factor_operands(strategy: Mapping[str, Any]) -> list[tuple[str, int]]`
  - `evaluate_claim_case(case: Mapping[str, Any], strategy: Mapping[str, Any] | None, assumptions: list[str]) -> dict[str, Any]` — `{"passed": bool, "reason": str | None}`

채점 규칙은 셋이다.

- `period` — 필터와 랭킹의 팩터 피연산자 가운데 하나라도 문장의 기간을 선택된 `timeframe`의 봉 개수로 환산한 값과 같으면 통과다. 하나라도 맞으면 통과로 보는 이유는 필터와 랭킹 중 어디에 기간을 쓸지는 요청마다 다르기 때문이다.
- `no-approximation` — 금지 팩터가 필터에도 랭킹에도 없고 `assumptions`가 비어 있지 않으면 통과다. 금지 목록이 빈 케이스는 `assumptions`만 본다. 스키마가 필터를 최소 한 개 요구하므로 "아무 조건도 만들지 않기"는 애초에 불가능하고, 그래서 말없이 근사하는 것을 잡는 데 초점을 둔다.
- `synonym` — 기대 팩터가 필터나 랭킹에 나오면 통과다. 임계값은 보지 않는다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`tests/test_claim_runner.py` 끝에 붙인다. 첫 줄의 임포트는 파일 상단의 기존 `from tests.claim_runner import load_claim_cases`와 한 줄로 합친다.

```python
from tests.claim_runner import candles_for_days, evaluate_claim_case, factor_operands


def strategy_with(factor: str, period: int, timeframe: str = "1D") -> dict:
    return {
        "timeframe": timeframe,
        "filters": {
            "type": "GROUP",
            "logic": "AND",
            "children": [
                {
                    "type": "CONDITION",
                    "left": {"type": "FACTOR", "factor": factor, "period": period},
                    "operator": "GT",
                    "right": {"type": "VALUE", "value": 0},
                }
            ],
        },
        "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
        "selection": {"topN": 5},
        "rebalance": {"period": "WEEKLY"},
    }


def test_a_day_count_becomes_candles_of_the_chosen_timeframe():
    assert candles_for_days(365, "1D") == 365
    assert candles_for_days(10, "4H") == 60
    assert candles_for_days(2, "1H") == 48


def test_both_filter_and_ranking_operands_are_collected():
    operands = factor_operands(strategy_with("VOLATILITY", 250))

    assert ("VOLATILITY", 250) in operands
    assert ("RETURN", 20) in operands


def test_the_period_claim_passes_when_the_sentence_period_is_used():
    case = {"id": "C1-11", "claim": "period", "days": 250}

    assert evaluate_claim_case(case, strategy_with("VOLATILITY", 250), [])["passed"]


def test_the_period_claim_fails_when_a_default_period_wins():
    case = {"id": "C1-11", "claim": "period", "days": 250}

    result = evaluate_claim_case(case, strategy_with("VOLATILITY", 20), [])

    assert not result["passed"]
    assert "250" in result["reason"]


def test_the_no_approximation_claim_fails_when_a_forbidden_factor_is_used():
    case = {"id": "C2-01", "claim": "no-approximation", "forbiddenFactors": ["VOLUME_RATIO"]}

    result = evaluate_claim_case(case, strategy_with("VOLUME_RATIO", 20), ["거래대금은 표현할 수 없습니다."])

    assert not result["passed"]


def test_the_no_approximation_claim_fails_when_nothing_is_said():
    case = {"id": "C2-05", "claim": "no-approximation", "forbiddenFactors": []}

    result = evaluate_claim_case(case, strategy_with("RETURN", 20), [])

    assert not result["passed"]
    assert result["reason"] == "silent"


def test_the_no_approximation_claim_passes_when_the_limit_is_stated():
    case = {"id": "C2-05", "claim": "no-approximation", "forbiddenFactors": []}

    result = evaluate_claim_case(case, strategy_with("RETURN", 20), ["손절은 표현할 수 없습니다."])

    assert result["passed"]


def test_the_synonym_claim_passes_when_the_expected_factor_appears():
    case = {"id": "C3-08", "claim": "synonym", "expectedFactor": "VOLATILITY"}

    assert evaluate_claim_case(case, strategy_with("VOLATILITY", 20), [])["passed"]


def test_a_missing_strategy_fails_every_claim():
    case = {"id": "C3-08", "claim": "synonym", "expectedFactor": "VOLATILITY"}

    result = evaluate_claim_case(case, None, [])

    assert not result["passed"]
    assert result["reason"] == "no-strategy"


def test_an_unknown_claim_is_rejected_rather_than_counted_as_a_pass():
    with pytest.raises(ValueError):
        evaluate_claim_case({"id": "X", "claim": "새 명제"}, strategy_with("RETURN", 20), [])
```

- [ ] **Step 2: 실패를 확인한다**

실행: `python -m pytest tests/test_claim_runner.py -v`
기대: `ImportError: cannot import name 'candles_for_days'`

- [ ] **Step 3: 채점 함수를 쓴다**

`tests/claim_runner.py`의 임포트를 바꾸고 함수를 붙인다:

```python
import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from app.core.schema import TIMEFRAME_MINUTES, Timeframe


MINUTES_PER_DAY = 1_440


def load_claim_cases(fixtures_dir: Path) -> list[dict[str, Any]]:
    """명제 세트를 읽는다."""
    return json.loads((fixtures_dir / "claim_cases.json").read_text(encoding="utf-8"))


def candles_for_days(days: int, timeframe: str) -> int:
    """문장의 일수를 선택된 봉 개수로 환산한다. 암호화폐는 휴장이 없어 1년이 365봉이다."""
    return days * MINUTES_PER_DAY // TIMEFRAME_MINUTES[Timeframe(timeframe)]


def factor_operands(strategy: Mapping[str, Any]) -> list[tuple[str, int]]:
    """필터 트리와 랭킹에 쓰인 팩터와 기간을 모두 모은다."""
    found: list[tuple[str, int]] = []

    def visit(node: Mapping[str, Any]) -> None:
        if node.get("type") == "GROUP":
            for child in node["children"]:
                visit(child)
            return
        for side in ("left", "right"):
            operand = node[side]
            if operand["type"] == "FACTOR":
                found.append((operand["factor"], operand["period"]))

    visit(strategy["filters"])
    found.append((strategy["ranking"]["factor"], strategy["ranking"]["period"]))
    return found


def evaluate_claim_case(
    case: Mapping[str, Any], strategy: Mapping[str, Any] | None, assumptions: list[str]
) -> dict[str, Any]:
    """한 발화의 결과를 그 발화가 검증하는 명제로 채점한다."""
    if strategy is None:
        return {"passed": False, "reason": "no-strategy"}

    operands = factor_operands(strategy)
    factors = {factor for factor, _ in operands}
    claim = case["claim"]

    if claim == "period":
        wanted = candles_for_days(case["days"], strategy["timeframe"])
        if any(period == wanted for _, period in operands):
            return {"passed": True, "reason": None}
        used = sorted({period for _, period in operands})
        return {"passed": False, "reason": f"expected period {wanted}, got {used}"}

    if claim == "no-approximation":
        leaked = sorted(set(case["forbiddenFactors"]) & factors)
        if leaked:
            return {"passed": False, "reason": f"approximated with {leaked}"}
        if not assumptions:
            return {"passed": False, "reason": "silent"}
        return {"passed": True, "reason": None}

    if claim == "synonym":
        if case["expectedFactor"] in factors:
            return {"passed": True, "reason": None}
        return {"passed": False, "reason": f"expected {case['expectedFactor']}, got {sorted(factors)}"}

    raise ValueError(f"Unknown claim: {claim}")
```

- [ ] **Step 4: 통과를 확인한다**

실행: `python -m pytest tests/test_claim_runner.py -v`
기대: 13 passed

- [ ] **Step 5: 전체 회귀를 확인한다**

실행: `python -m pytest -q`
기대: 이전 196건에 새 13건이 더해져 209 passed, 3 deselected

- [ ] **Step 6: 커밋한다**

```bash
git add tests/claim_runner.py tests/test_claim_runner.py
git commit -m "test: 명제 세트 채점 규칙 구현

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: 라이브 측정 실행기

**Files:**
- Create: `tests/test_claim_live.py`

**Interfaces:**
- Consumes: `load_claim_cases`, `evaluate_claim_case` (Task 1·2)
- Produces: `reports/claim-live-<타임스탬프>.json`

리포트는 `summary`, `byClaim`, `excludingSeen`, `failures`, `results`를 담는다. `excludingSeen`이 필요한 이유는 C1-12와 C2-12가 이미 실패를 확인한 문장이라 개선폭을 부풀리기 때문이다.

- [ ] **Step 1: 실행기를 쓴다**

`tests/test_claim_live.py`:

```python
"""Opt-in GMS 명제 측정. 실행: python -m pytest -m live -k claim

프롬프트를 고치기 전과 후에 각각 3회 돌린다. 1회 측정은 모델의 비결정성과
구분되지 않는다.
"""

import json
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from app.core.errors import DSLValidationError, LLMCreditLimitError, LLMTimeoutError, LLMUpstreamError
from app.services.prompt_builder import load_prompt_templates
from app.services.translator import translate
from tests.claim_runner import evaluate_claim_case, load_claim_cases


MIN_CALL_INTERVAL_SECONDS = 2.1
FIXTURES_DIR = Path(__file__).parent / "fixtures"
REPORTS_DIR = Path(__file__).parent.parent / "reports"


@pytest.mark.live
def test_live_claim_cases_write_measurement_report():
    load_prompt_templates()
    cases = load_claim_cases(FIXTURES_DIR)
    results = []
    last_call_at = None

    for case in cases:
        if last_call_at is not None:
            time.sleep(max(0, MIN_CALL_INTERVAL_SECONDS - (time.monotonic() - last_call_at)))
        try:
            result = translate(case["userInput"])
            last_call_at = time.monotonic()
        except LLMCreditLimitError:
            time.sleep(60)
            result = translate(case["userInput"])
            last_call_at = time.monotonic()
        except DSLValidationError as error:
            results.append(_error_result(case, "format", error.attempts))
            continue
        except (LLMTimeoutError, LLMUpstreamError):
            results.append(_error_result(case, "upstream", None))
            continue

        strategy = result.value.model_dump(mode="json") if result.value is not None else None
        assumptions = result.meta.assumptions if result.meta is not None else []
        results.append({
            "id": case["id"],
            "claim": case["claim"],
            "seen": bool(case.get("seen")),
            "userInput": case["userInput"],
            "attempts": result.meta.attempts if result.meta is not None else None,
            "elapsedMs": result.meta.elapsedMs if result.meta is not None else None,
            "assumptions": assumptions,
            "failureReason": None,
            "strategy": strategy,
            **evaluate_claim_case(case, strategy, assumptions),
        })

    report = _build_report(results)
    REPORTS_DIR.mkdir(exist_ok=True)
    report_path = REPORTS_DIR / f"claim-live-{datetime.now(UTC):%Y%m%dT%H%M%SZ}.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    assert len(results) == 36


def _error_result(case: dict, failure_reason: str, attempts: int | None) -> dict:
    return {
        "id": case["id"],
        "claim": case["claim"],
        "seen": bool(case.get("seen")),
        "userInput": case["userInput"],
        "attempts": attempts,
        "elapsedMs": None,
        "assumptions": [],
        "failureReason": failure_reason,
        "strategy": None,
        "passed": False,
        "reason": failure_reason,
    }


def _build_report(results: list[dict]) -> dict:
    unseen = [result for result in results if not result["seen"]]
    return {
        "summary": _counts(results),
        "byClaim": {
            claim: _counts([result for result in results if result["claim"] == claim])
            for claim in ("period", "no-approximation", "synonym")
        },
        # 이미 실패를 확인한 두 문장을 뺀 값. 아는 문장만 고쳐서 오른 것을 개선으로 세지 않는다.
        "excludingSeen": {
            "summary": _counts(unseen),
            "byClaim": {
                claim: _counts([result for result in unseen if result["claim"] == claim])
                for claim in ("period", "no-approximation", "synonym")
            },
        },
        "failures": [
            {"id": result["id"], "claim": result["claim"], "reason": result["reason"]}
            for result in results
            if not result["passed"]
        ],
        "results": results,
    }


def _counts(results: list[dict]) -> dict:
    passed = sum(result["passed"] for result in results)
    elapsed = sorted(result["elapsedMs"] for result in results if result["elapsedMs"] is not None)
    return {
        "total": len(results),
        "passed": passed,
        "passRate": (passed / len(results)) if results else 0,
        "medianElapsedMs": elapsed[len(elapsed) // 2] if elapsed else None,
    }
```

- [ ] **Step 2: 수집만 되는지 확인한다**

실행: `python -m pytest -m live -k claim --collect-only -q`
기대: `tests/test_claim_live.py::test_live_claim_cases_write_measurement_report` 한 건이 수집된다

- [ ] **Step 3: 기본 실행에서 제외되는지 확인한다**

실행: `python -m pytest -q`
기대: 209 passed, 4 deselected. deselected가 3에서 4로 는 것이 새 라이브 테스트가 기본 실행에서 빠졌다는 뜻이다

- [ ] **Step 4: 리포트를 무시 목록에 넣는다**

`.gitignore`의 6~8행에 다른 리포트 세 줄이 있다. 그 아래에 한 줄 더 넣는다. 넣지 않으면 측정할 때마다 리포트가 스테이징에 올라온다.

```
reports/claim-live-*.json
```

확인: `git status --short reports/`가 아무것도 내지 않는다.

- [ ] **Step 5: 커밋한다**

```bash
git add tests/test_claim_live.py .gitignore
git commit -m "test: 명제 세트 라이브 측정 실행기 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: 홀드아웃에서 본 케이스를 뺀다

**Files:**
- Modify: `tests/fixtures/holdout_inputs.md`
- Modify: `tests/test_holdout_runner.py:39-43`
- Modify: `tests/test_holdout_live.py:69`

H-20과 H-22의 실패 내용을 이미 읽었으므로 결정 12의 규칙상 홀드아웃 자격을 잃었다. 두 문장은 Task 1에서 C2-12·C1-12로 옮겨 두었다. 여기서는 홀드아웃에서 빼기만 한다. 28건이 26건이 되고 in-scope가 9건에서 7건이 된다. 빈자리는 2차 수집 때 채운다.

- [ ] **Step 1: 단언을 먼저 고쳐 실패시킨다**

`tests/test_holdout_runner.py`의 `test_the_holdout_set_loads_with_its_categories`를 바꾼다:

```python
def test_the_holdout_set_loads_with_its_categories():
    cases = load_holdout_cases(FIXTURES_DIR)

    assert len(cases) == 26
    assert cases[0]["id"] == "H-01"
    assert all(case["userInput"] for case in cases)
    assert sum(case["scope"] == "in-scope" for case in cases) == 7
    assert sum(case["scope"] == "out-of-scope" for case in cases) == 19
```

- [ ] **Step 2: 실패를 확인한다**

실행: `python -m pytest tests/test_holdout_runner.py -v`
기대: FAIL — `assert 28 == 26`

- [ ] **Step 3: 두 행을 뺀다**

`tests/fixtures/holdout_inputs.md`의 표에서 `H-20`과 `H-22`로 시작하는 두 행을 지운다. 나머지 ID는 다시 매기지 않는다. 번호를 밀면 예전 리포트와 대조가 안 된다.

같은 파일의 「갈래 분포」 표도 고친다.

```markdown
| 갈래 | 건수 |
|---|---|
| 지원 범위 밖 요청 | 15 |
| 조건이 붙은 요청 | 3 |
| 전략이 아닌 입력 | 4 |
| 모호한 요청 | 3 |
| 여러 조건을 쌓는 요청 | 1 |
| 단순 랭킹 요청 | 0 |
```

「출처의 한계」 절 위에 왜 뺐는지 남긴다.

```markdown
## 뺀 케이스

| ID | 문장 | 왜 뺐나 | 어디로 갔나 |
|---|---|---|---|
| H-20 | 거래량 바닥이라 … 손도 안 댑니다 | 실패 내용을 읽고 프롬프트 설계의 근거로 썼다 | `claim_cases.json` C2-12 |
| H-22 | 1년 내내 계단식 우하향 … | 같음 | `claim_cases.json` C1-12 |

한 번 들여다본 발화는 홀드아웃이 아니다. 빈자리는 2차 수집으로 채운다.
```

- [ ] **Step 4: 라이브 단언을 고친다**

`tests/test_holdout_live.py`의 마지막 단언을 바꾼다.

```python
    assert len(results) == 26
```

- [ ] **Step 5: 통과를 확인한다**

실행: `python -m pytest -q`
기대: 209 passed, 4 deselected

- [ ] **Step 6: 커밋한다**

```bash
git add tests/fixtures/holdout_inputs.md tests/test_holdout_runner.py tests/test_holdout_live.py
git commit -m "test: 설계 근거로 본 홀드아웃 두 건을 명제 세트로 옮긴다

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: 수정 전 기준선 측정

**Files:**
- Modify: `docs/DESIGN-PROMPT-AXES.md` (기준선 표 추가)

여기서는 코드를 고치지 않는다. `system.txt`가 아직 옛 상태일 때의 숫자를 남기는 것이 목적이다.

측정 비용은 회당 명제 36 + 골든 34 + 편집 12 + 홀드아웃 26 = 108건이고 3회면 324건이다. 호출 간격 2.1초라 회당 약 8분이다.

- [ ] **Step 1: 네 세트를 3회 돌린다**

세 번 반복한다. `-p no:randomly`는 쓰지 않는다. 회차 사이에 코드를 고치지 않는다.

```bash
python -m pytest -m live -k claim -q
python -m pytest -m live -k golden -q
python -m pytest -m live -k edit -q
python -m pytest -m live -k holdout -q
```

- [ ] **Step 2: 리포트에서 숫자를 뽑는다**

```bash
python - <<'PY'
import glob, json
for prefix in ("claim", "golden", "edit", "holdout"):
    for path in sorted(glob.glob(f"reports/{prefix}-live-*.json"))[-3:]:
        report = json.load(open(path, encoding="utf-8"))
        print(prefix, path.split("-live-")[1][:15], json.dumps(report["summary"], ensure_ascii=False))
PY
```

- [ ] **Step 3: 홀드아웃 분포도 뽑는다**

```bash
python - <<'PY'
import glob, json
from collections import Counter
path = sorted(glob.glob("reports/holdout-live-*.json"))[-1]
factors, periods, thresholds = Counter(), Counter(), Counter()

def walk(node):
    if node.get("type") == "GROUP":
        for child in node["children"]:
            walk(child)
        return
    for side in ("left", "right"):
        operand = node[side]
        if operand["type"] == "FACTOR":
            factors[operand["factor"]] += 1
            periods[operand["period"]] += 1
        else:
            thresholds[operand["value"]] += 1

for result in json.load(open(path, encoding="utf-8"))["results"]:
    if result["strategy"]:
        walk(result["strategy"]["filters"])
print("팩터", dict(factors))
print("기간", dict(sorted(periods.items())))
print("임계값", dict(sorted(thresholds.items())))
PY
```

- [ ] **Step 4: 기준선 표를 문서에 적는다**

`docs/DESIGN-PROMPT-AXES.md`의 「검증」 절 끝에 「측정 결과」 절을 만든다. 요약이 아니라 회차 숫자를 그대로 적는다. 편차를 나중에 볼 수 있어야 한다.

```markdown
## 측정 결과

### 수정 전 (2026-09-__, system.txt <커밋 SHA>)

| 세트 | 1회 | 2회 | 3회 | 중앙값 |
|---|---|---|---|---|
| 명제 1 기간 | /12 | /12 | /12 | |
| 명제 2 근사 금지 | /12 | /12 | /12 | |
| 명제 3 동의어 | /12 | /12 | /12 | |
| 명제 전체 (seen 제외) | /34 | /34 | /34 | |
| 골든 | /31 | /31 | /31 | |
| 편집 | /12 | /12 | /12 | |

홀드아웃 26건 분포 (1회차): 팩터 …, 기간 …, 임계값 …
```

- [ ] **Step 5: 커밋한다**

```bash
git add docs/DESIGN-PROMPT-AXES.md
git commit -m "docs: 프롬프트 수정 전 기준선 측정 결과 기록

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

리포트 JSON은 커밋하지 않는다. `reports/`는 `.gitignore`에 있다.

---

### Task 6: system.txt 재구성

**Files:**
- Modify: `app/prompts/system.txt`

설계의 1~4절을 그대로 옮긴다. 파일 전체를 다시 쓰되 마지막 출력 형식 규칙과 `assumptions` 한국어 규칙은 지금 문장을 그대로 유지한다. `{{dsl_schema}}` 자리표시자를 지우지 않는다.

- [ ] **Step 1: 자리표시자 계약을 먼저 확인한다**

실행: `python -m pytest tests/test_prompt_templates.py -v`
기대: 3 passed. 수정 뒤에도 이 셋이 통과해야 한다

- [ ] **Step 2: 새 `system.txt`를 쓴다**

```text
You translate a user's strategy request into the supplied DSL schema.

DSL schema:
{{dsl_schema}}

Decide in this order, and stop at the first step that answers the question:
1. A number, period, or factor the user wrote in the request.
2. The standard readings below, only when that exact phrase appears in the request.
3. The factor definitions below.
4. The defaults.

What each factor measures, and what it does not:
- RETURN(N): return over the last N candles, in percentage points.
- RSI(N): the ratio of gains to losses, 0 to 100.
- SMA_DISPARITY(N): how far the close sits from its N-candle moving average, in percentage points. Comparing two of them tells you whether the short average leads the long one. It cannot tell you when they crossed.
- VOLATILITY(N): the standard deviation of one-candle returns over N candles, in percentage points. It says nothing about direction: a high value is not a rising coin.
- VOLUME_RATIO(N): current volume divided by average volume over N candles, a multiple. It is relative to the coin's own past, so it is not trading value, liquidity, or market capitalisation. A thinly traded coin clears 1.5 on a single busy candle.
- DRAWDOWN(N): how far the close sits below the highest close of the last N candles, in percentage points, never above 0. It is not the peak-to-trough maximum drawdown. A value of 0 means the coin is at a new high.

Every factor is a single value read at the current candle. You cannot compare today against yesterday, and you cannot detect the moment a crossing, breakout, or recovery happened. Express those as the state they leave behind, and say in assumptions that it is an approximation.

These have no factor at all: absolute trading value, market capitalisation, liquidity and order-book depth; naming or excluding a specific coin; conditions about another coin; ranking by two factors at once; stop loss, take profit, holding period, scaling in or out, position sizing; candle shapes and chart patterns; and outside data such as news, communities, on-chain flows, futures, premiums, or delisting notices. When one of these is the point of the request, do not approximate it with a factor. Build the rest of the request, and say in assumptions, in Korean, which part has no expression here. Approximate only when the axis exists and the wording differs.

The schema always requires a strategy with at least one condition, so when nothing in the request can be expressed, return a neutral strategy built from the defaults and say in assumptions, in Korean, that the request itself could not be expressed. Never present that neutral strategy as if it answered the request.

Domain rules:
- The root of filters is always a GROUP node, even when only one condition remains after an edit.
- period means the number of candles in the selected timeframe. Choose the timeframe matching the user's unit; do not convert or multiply periods.
- RETURN, SMA_DISPARITY, VOLATILITY, and DRAWDOWN use percentage points. RSI uses 0 to 100. VOLUME_RATIO is a multiple, not a percentage.
- Write a percentage as the number of percentage points, never as a fraction: a 5% return is RETURN 5, not RETURN 0.05; a 3% gap from the moving average is SMA_DISPARITY 3; 2% volatility is VOLATILITY 2; a 20% drawdown is DRAWDOWN -20. RSI 70 is RSI 70. 1.5 times volume is VOLUME_RATIO 1.5.
- This DSL says what to hold, not when to enter or exit. Every rebalance rebuilds the holdings from filters and ranking: a coin is bought when it enters the selection and sold when it drops out. Express a request to sell, avoid, or exit as a filter that keeps the unwanted coins out of the selection, and say in assumptions, in Korean, that selling happens by dropping out of the selection at the next rebalance.
- When the user gives no period, use these: RSI 14, RETURN 20, SMA_DISPARITY 20, VOLATILITY 20, VOLUME_RATIO 20, DRAWDOWN 60. A period the user did give always wins over these. State every chosen default in assumptions.
- Use timeframe 1D, topN 5, and WEEKLY rebalance unless the user asks for something else. State every chosen default in assumptions.

Period words in a 1D timeframe: 단기 5, 장기 20, 1주 7, 2주 14, 1개월 30, 3개월 90, 반년 180, 1년 365. Crypto has no market holidays, so a year is 365 candles. 이상 and 이하 are inclusive (GTE, LTE); 초과 and 미만 are exclusive (GT, LT). A phrase about something increasing or spiking is inclusive at its threshold.

Standard readings. Use one only when its phrase appears in the request as written; judge every other wording from the factor definitions above. Rank by RETURN 20 DESC unless a line below says otherwise:
- 거래량 급증, 거래량이 갑자기 증가: VOLUME_RATIO 20 GTE 1.5, ranked by VOLUME_RATIO 20 DESC.
- 많이 떨어진 코인의 반등: DRAWDOWN 60 LTE -20 AND RSI 14 GT 30.
- 급락 후 회복: DRAWDOWN 20 LTE -15 AND RSI 14 GT 30.
- 볼린저밴드 하단까지 떨어졌다가 회복: SMA_DISPARITY 20 LT -5 AND RSI 14 GT 30.
- 신고가 돌파: DRAWDOWN 20 GTE 0.
- 골든크로스, 단기 이동평균선이 장기 이동평균선을 돌파: SMA_DISPARITY 5 GT SMA_DISPARITY 20, comparing one factor against the other rather than against a number.
- 변동성이 크다: VOLATILITY 20 GT 3. 변동성이 작다, 꾸준하다: VOLATILITY 20 LT 5.
- 급등한 것은 피하고 꾸준히 상승: RETURN 60 GT 0 AND VOLATILITY 20 LT 5, ranked by RETURN 60 DESC.
- 상승 추세, 상승 추세가 강하다, with no period in the request: RETURN 20 GT 0.
- 손실은 짧게 끊고 상승 추세는 오래 가져간다: RETURN 60 GT 0, ranked by RETURN 60 DESC. A stop loss is not expressible as a filter; say so in assumptions.
- 한 달 동안 계속 강했다: RETURN 30 GT 0, ranked by RETURN 30 DESC.

Write every line in assumptions in Korean, regardless of the language of these instructions. End every line with the polite "-습니다" form, as in "기본 시간프레임 1D를 사용했습니다."; never the plain "-다" form such as "사용했다".

Return one JSON object that follows the supplied schema. If the request is not a strategy description or lacks details, still create a strategy and record every value you chose in assumptions as a short line.
Output exactly one JSON object.
Do not use a code fence.
Do not add text before or after the JSON object.
Do not add fields that are absent from the schema.
```

- [ ] **Step 3: 자리표시자 계약이 살아 있는지 확인한다**

실행: `python -m pytest -q`
기대: 209 passed, 4 deselected

- [ ] **Step 4: 명제 세트를 한 번 돌려 방향을 본다**

실행: `python -m pytest -m live -k claim -q`

리포트의 `failures`를 읽는다. 여기서 **개별 실패를 보고 프롬프트를 고치지 않는다.** 방향이 맞는지만 본다. 통과율이 기준선보다 낮으면 Step 2로 돌아가 설계 1~4절 가운데 빠뜨린 것이 있는지 본다.

- [ ] **Step 5: 커밋한다**

```bash
git add app/prompts/system.txt
git commit -m "feat: 프롬프트를 팩터 축 정의 중심으로 재구성

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: 수정 후 측정과 합격 판정

**Files:**
- Modify: `docs/DESIGN-PROMPT-AXES.md` (결과표)
- Modify: `docs/DECISIONS.md` (결정 로그)
- Modify: `docs/TROUBLESHOOTING.md` (막혔다 풀린 것이 있을 때만)

- [ ] **Step 1: 네 세트를 3회 돌린다**

Task 5 Step 1과 같은 명령을 같은 순서로 돌린다.

- [ ] **Step 2: 기준선과 나란히 표로 만든다**

Task 5 Step 2·3의 스크립트를 다시 돌리고 전후를 한 표에 넣는다. 명제 세트는 전체와 `excludingSeen`을 나눠 적는다.

- [ ] **Step 3: 합격을 판정한다**

| 항목 | 기준 |
|---|---|
| 명제 1·2 | 3회 통과율의 중앙값이 기준선 중앙값보다 높고, 가장 나쁜 회차가 기준선의 가장 좋은 회차보다 낮지 않다 |
| 명제 3 | 기준선을 유지한다 |
| 골든 34 | 손실 5건 이내 |
| 편집 12 | 12/12 유지 |
| 홀드아웃 26 | 기록만 한다 |

골든이 깨진 건은 하나씩 본다. 표 문구가 그대로 들어간 케이스가 깨졌으면 4절의 적용 조건이 너무 좁은 것이니 진짜 회귀다. 표를 느슨하게 끌어 쓰던 케이스가 깨졌으면 의도한 변화다.

명제가 다 통과했는데도 출력이 미덥지 않으면 설계 4층(블라인드 A/B)을 연다. 같은 발화에 수정 전후 두 출력을 만들고 출처를 가린 채 사람이 고른다. 이 계획에는 넣지 않았다 — 1층이 통과하면 필요 없고, 통과하지 못하면 그 전에 Task 6으로 돌아가기 때문이다.

- [ ] **Step 4: 미달이면 Task 6 Step 2로 돌아간다**

세 번 미달하면 프롬프트를 더 고치지 않는다. 설계가 틀린 것이므로 [DESIGN-PROMPT-AXES.md](DESIGN-PROMPT-AXES.md)를 다시 연다.

- [ ] **Step 5: 결정 로그에 적는다**

`docs/DECISIONS.md`에 날짜와 함께 결정을 적는다. 결정 12가 요구한 단서를 이어 적는다 — 명제 세트는 정답이 발화에 들어 있어 프롬프트에 반영할 정답지가 아니라는 것, 그리고 골든 통과율은 여전히 회귀 지표라는 것.

- [ ] **Step 6: 커밋한다**

```bash
git add docs/DESIGN-PROMPT-AXES.md docs/DECISIONS.md
git commit -m "docs: 프롬프트 축 재구성 측정 결과와 결정 기록

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## 비용

| 항목 | 값 |
|---|---|
| GMS 호출 | 기준선 324건 + 수정 후 324건 + 중간 확인 36건 ≈ 684건 |
| 측정 시간 | 회당 약 8분, 총 약 50분 |
| 코드 | 신규 3파일, 수정 4파일 |
