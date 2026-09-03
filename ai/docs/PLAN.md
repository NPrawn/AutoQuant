# AI 서버 구현 계획서 (역할 1: 자연어 → 전략 DSL 변환)

**목표:** Spring 백엔드가 HTTP로 호출하면, 사용자의 자연어 전략 설명을 검증된 전략 DSL(JSON)로 변환해 돌려주는 FastAPI 서버를 만든다.

**범위:** 역할 1(자연어 → DSL)까지만. 역할 2(LightGBM 견고성 점수 추론)는 이번 계획서에서 제외하되, 라우터와 서비스 계층을 나중에 모듈만 추가하면 되도록 나눠 둔다.

**기술 스택:** Python 3.11, FastAPI, Pydantic v2, pytest, `openai` SDK(>=1.60). LLM 호출은 SSAFY GMS 게이트웨이 경유 — 공급자 교체는 `LLM_BASE_URL` 변경으로 끝난다 (DECISIONS 결정 4).

**기간/인원:** 5주, 1명.

---

## 0. 설계 원칙

- **DSL 스펙은 `app/core/schema.py` 한 곳에만 정의한다.** 라우터·서비스·테스트·프롬프트 어디에도 필드명이나 허용값을 다시 적지 않는다. 프롬프트에 들어갈 스펙 문구조차 schema에서 뽑아 만든다. 스펙 교체 시 수정 대상은 `schema.py`와 프롬프트 `.txt` 두 종류뿐이어야 한다.
- **API 키는 환경변수로만 읽는다.** `app/core/config.py`가 유일한 진입점이고, 다른 파일은 `os.environ`을 직접 만지지 않는다. `.env`는 `.gitignore`에 넣고 `.env.example`에 키 이름만 남긴다.
- **프롬프트는 파이썬 파일에 문자열로 박지 않고 `app/prompts/*.txt`로 분리한다.** 프롬프트 수정에 코드 배포가 딸려오지 않게 한다.
- **LLM 응답 검증 실패 시 오류 메시지를 프롬프트에 붙여 최대 2회 재시도한다.** 총 LLM 호출은 최대 3회(최초 1 + 재시도 2).
- 외부 의존(LLM API)은 인터페이스 뒤에 숨겨서, 테스트가 네트워크 없이 돈다.

---

## 1. 디렉토리 구조와 각 파일의 책임

```
ai-server/
├── app/
│   ├── main.py
│   ├── api/
│   │   ├── __init__.py
│   │   ├── deps.py
│   │   └── v1/
│   │       ├── __init__.py
│   │       ├── health.py
│   │       └── strategy.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── schema.py
│   │   ├── config.py
│   │   ├── errors.py
│   │   └── logging.py
│   ├── services/
│   │   ├── __init__.py
│   │   ├── translator.py
│   │   ├── llm_client.py
│   │   ├── prompt_builder.py
│   │   └── validator.py
│   └── prompts/
│       ├── system.txt
│       ├── user.txt
│       └── retry.txt
├── tests/
│   ├── conftest.py
│   ├── fixtures/
│   │   ├── golden_cases.json
│   │   └── llm_responses.py
│   ├── test_schema.py
│   ├── test_validator.py
│   ├── test_prompt_builder.py
│   ├── test_translator.py
│   └── test_api_strategy.py
├── .env.example
├── requirements.txt
├── pyproject.toml
└── README.md
```

### 파일별 책임

**`app/main.py`**
FastAPI 앱 객체를 만들고 라우터를 붙인다. 예외 핸들러를 등록해 `core/errors.py`의 예외 타입을 HTTP 응답으로 바꾼다. 기동 시 설정 검증(필수 환경변수 존재 확인)을 여기서 한다. CORS는 두지 않는다 — 이 서버는 Spring만 호출한다. 비즈니스 로직은 한 줄도 두지 않는다.

**`app/api/v1/strategy.py`**
`POST /api/v1/ai/strategies/translate` 엔드포인트 하나를 정의한다. 요청 본문을 받아 `services/translator.py`에 넘기고, 결과를 응답 모델로 감싸 반환한다. LLM이나 검증을 직접 호출하지 않는다. 라우터는 얇게 유지한다.

**`app/api/v1/health.py`**
`GET /health`(프로세스 생존)와 `GET /health/ready`(설정·프롬프트 파일 로드 가능 여부)를 제공한다. LLM API를 호출하지 않는다. 과금 요인이 헬스체크에 붙으면 안 된다.

**`app/api/deps.py`**
라우터가 쓸 의존성 주입 함수를 모은다. 설정 객체와 translator 인스턴스를 만들어 주입한다. 테스트에서 이 의존성만 교체하면 가짜 LLM으로 API 테스트가 돌아간다.

**`app/core/schema.py`** — 이 프로젝트에서 가장 중요한 파일
DSL 스펙의 단일 정의처다. `Strategy`, `Universe`, `Signal`, `Feature`, `Portfolio`, `Risk`, `Execution` 모델과 열거형(`SignalType`, `FeatureType`, `PortfolioType`, `Rebalance`, `Timeframe`)을 담는다. 범위 제약을 필드 제약으로 표현하고, 조합 제약(예: `select_top`이 `top_by_trading_value`를 넘을 수 없다)도 모델 검증자로 여기에 둔다. API 요청·응답 모델도 같은 파일에 둔다 — 응답이 `Strategy`를 감싸는 형태라 분리하면 스펙이 두 군데로 흩어진다.
스펙이 교체될 때 수정하는 파일은 원칙적으로 여기 하나다.

**`app/core/config.py`**
환경변수를 읽어 설정 객체 하나를 만든다. `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`, `LLM_TIMEOUT_SECONDS`(기본 30), `MAX_RETRIES`(기본 2), `LOG_LEVEL`(기본 INFO), `APP_ENV`를 다룬다. `LLM_PROVIDER`는 두지 않는다 — 게이트웨이가 하나뿐이라 분기할 값이 아니다. 필수값이 없으면 앱 기동 단계에서 죽는다 — 첫 요청에서 죽으면 안 된다. 키 값 자체가 로그·응답 어디에도 나가지 않도록 표현(`repr`)을 막는다.

**`app/core/errors.py`**
도메인 예외 타입과 오류 코드를 정의한다. `LLMTimeoutError`, `LLMUpstreamError`, `DSLValidationError`(재시도 소진 포함), `PromptTooLongError`. 각 예외는 HTTP 상태 코드와 오류 코드 문자열을 갖는다. 예외를 응답으로 바꾸는 변환은 `main.py`의 핸들러가 맡는다.

**`app/core/logging.py`**
구조화 로깅 설정. 요청 ID를 붙이고, LLM 호출마다 시도 횟수·소요 시간·토큰 수·성공 여부를 남긴다. 사용자 입력 원문은 남기되 API 키는 남기지 않는다.

**`app/services/translator.py`**
역할 1의 핵심 흐름을 지휘한다. 프롬프트 구성 → LLM 호출 → 응답 파싱 → 검증 → 실패 시 오류를 붙여 재시도, 이 순서를 관장한다. 재시도 횟수와 종료 조건을 여기서 판단한다. 프롬프트 문구를 직접 만들지 않고(`prompt_builder` 담당), HTTP를 직접 치지 않는다(`llm_client` 담당).

**`app/services/llm_client.py`**
LLM 공급자와 통신하는 유일한 파일. 메시지 목록을 받아 텍스트를 돌려주는 함수 하나를 노출한다. 타임아웃과 HTTP 오류를 `core/errors.py`의 예외로 번역한다. 공급자를 바꾸면 이 파일만 고친다. 테스트에서는 같은 시그니처의 가짜 구현으로 대체한다.

**`app/services/prompt_builder.py`**
`app/prompts/*.txt`를 읽어 자리표시자를 채운다. 시스템 프롬프트에는 `core/schema.py`에서 생성한 JSON Schema를 끼워 넣어, 스펙이 바뀌면 프롬프트도 따라오게 한다. 재시도용 프롬프트에는 직전 LLM 출력과 검증 오류 메시지를 붙인다. 프롬프트 파일은 기동 시 한 번 읽어 캐시한다.

**`app/services/validator.py`**
LLM이 뱉은 원문 텍스트를 JSON으로 파싱하고 `Strategy` 모델로 검증한다. 코드 블록 표시가 섞여 오는 경우를 벗겨내는 정도의 관용은 여기서만 처리한다. 검증 실패 시 LLM이 이해할 수 있는 형태(필드 경로 + 무엇이 잘못됐는지)로 오류 문자열을 만들어 돌려준다 — 이 문자열 품질이 재시도 성공률을 좌우한다. 검증 규칙 자체는 정의하지 않고 `schema.py`에 위임한다.

**`app/prompts/system.txt`**
역할 지시, DSL 스펙(자리표시자로 주입), 출력 형식 규칙(JSON만, 설명 문장 금지), 모호한 입력을 다루는 기본값 정책을 담는다.

**`app/prompts/user.txt`**
사용자 자연어 입력을 감싸는 최소 템플릿.

**`app/prompts/retry.txt`**
직전 출력과 검증 오류를 제시하고 고쳐서 다시 내라는 지시.

---

## 2. 데이터 모델 (`app/core/schema.py`)

아래는 정의할 대상 목록이지 구현 코드가 아니다.

### 열거형

| 이름 | 값 |
|---|---|
| `SignalType` | `CROSS_SECTIONAL_RANK`, `INDICATOR_CONDITION` |
| `FeatureType` | `RETURN`, `RSI`, `SMA`, `EMA`, `VOLUME` |
| `PortfolioType` | `EQUAL_WEIGHT`, `RANK_WEIGHTED` |
| `Rebalance` | `DAILY`, `WEEKLY`, `MONTHLY` |
| `Timeframe` | `4H`, `1D` |

### DSL 모델

| 모델 | 필드 | 타입·제약 | 기본값 |
|---|---|---|---|
| `Universe` | `market` | `"UPBIT_KRW"` 고정 | `UPBIT_KRW` |
| | `top_by_trading_value` | int, 5~30 | 없음(필수) |
| `Feature` | `type` | `FeatureType` | 없음 |
| | `lookback_days` | int, 1~365 | 지표별 관행값 |
| `Signal` | `type` | `SignalType` | 없음 |
| | `feature` | `Feature` | 없음 |
| | `filters` | 후보 필터 배열, 최대 3개 | `[]` |
| | `select_top` | int, 1~30 | 없음 |
| `Portfolio` | `type` | `PortfolioType` | 없음 |
| | `weight_decay` | float 0~1 또는 null | null |
| | `max_asset_weight` | float, 0 초과 1 이하 | 0.34 |
| `Risk` | `stop_loss` | float 또는 null, -1~0 | null |
| | `take_profit` | float 또는 null, 0~10 | null |
| | `regime_gate` | bool | false |
| `Execution` | `rebalance` | `Rebalance` | 없음 |
| | `timeframe` | `Timeframe` | `1D` |
| `Strategy` | `universe`, `signal`, `portfolio`, `risk`, `execution` | 각 하위 모델 | 없음 |

**필터 항목 하나의 내부 구조는 아직 확정되지 않았다.** 개수 상한(최대 3개)과 동작 순서(거르기 → 줄 세우기 → 자르기)만 확정이다. 확정되면 이 표에 추가한다.

### 교차 검증 규칙 (`Strategy` 수준)

거부 층 (검증 실패 → 재시도):

- `signal.select_top` ≤ `universe.top_by_trading_value`
- `portfolio.weight_decay`는 `type`이 `RANK_WEIGHTED`일 때만 채운다. `EQUAL_WEIGHT`면 null
- 알 수 없는 필드는 거부한다. LLM이 없는 필드를 지어내면 검증 실패로 잡혀 재시도로 이어져야 한다.

경고 층 (200으로 통과시키되 `meta.warnings`에 담는다):

- `signal.feature`의 기간이 `execution.rebalance` 주기의 5배 미만
- `portfolio.max_asset_weight` × `signal.select_top` < 1.0 (1~2종목 집중)
- 극단적 손절·익절

경고로 내린 이유는 **역할 2가 잡아야 할 것을 서버가 미리 막으면 모델이 배울 사례가 사라지기 때문**이다. 자세한 근거는 [DECISIONS.md의 "검증을 세 층으로 나눈다"](DECISIONS.md).

### API 모델

| 모델 | 필드 |
|---|---|
| `TranslateRequest` | `userInput`(str, 1~2000자), `currentStrategy`(`Strategy` 또는 null — 있으면 수정, 없으면 신규) |
| `TranslateResponse` | `strategy`(`Strategy`), `meta`(`TranslateMeta`) |
| `TranslateMeta` | `attempts`(int), `model`(str), `elapsedMs`(int), `warnings`(str 목록), `assumptions`(str 목록), `changes`(str 목록) |
| `ErrorResponse` | `code`(str), `message`(str), `detail`(object 또는 null) |

---

## 3. API 명세

### `POST /api/v1/ai/strategies/translate`

요청:

```json
{
  "userInput": "거래대금 상위 20개 코인 중에서 최근 20일 수익률이 가장 높은 5개를 골라 동일비중으로 주간 리밸런싱하고, 손절은 -10%로 걸어줘"
}
```

성공 응답 `200 OK` — **아래 `strategy` 본문은 임시 DSL 시절 예시다.** 정본은 [Strategy 도메인 명세서](../../docs/Strategy%20도메인%20명세서.md)와 `app/core/schema.py`고, 실제 예시는 L-3에서 코드로 뽑는다:

```json
{
  "strategy": {
    "universe": {
      "market": "UPBIT_KRW",
      "top_by_trading_value": 20
    },
    "signal": {
      "type": "CROSS_SECTIONAL_RANK",
      "feature": { "type": "RETURN", "lookback_days": 20 },
      "filters": [],
      "select_top": 5
    },
    "portfolio": { "type": "EQUAL_WEIGHT", "weight_decay": null, "max_asset_weight": 0.34 },
    "risk": { "stop_loss": -0.1, "take_profit": null, "regime_gate": false },
    "execution": { "rebalance": "WEEKLY", "timeframe": "1D" }
  },
  "meta": {
    "attempts": 1,
    "model": "gpt-5.4-mini",
    "elapsedMs": 2140,
    "warnings": [],
    "assumptions": [],
    "changes": []
  }
}
```

실패 응답 `422 Unprocessable Entity` — 재시도 2회를 모두 쓰고도 검증을 통과하지 못한 경우:

```json
{
  "code": "DSL_VALIDATION_FAILED",
  "message": "생성된 전략이 DSL 스펙을 만족하지 못했습니다.",
  "detail": {
    "attempts": 3,
    "last_errors": [
      "signal.select_top: 값 40은 허용 범위(1~30)를 벗어납니다.",
      "execution.timeframe: '1H'는 허용값이 아닙니다. (4H, 1D)"
    ]
  }
}
```

### `GET /health`

`200 OK` — `{"status": "ok"}`

### `GET /health/ready`

`200 OK` — `{"status": "ready", "prompts_loaded": true, "config_ok": true}`
설정이나 프롬프트 로드에 실패하면 `503`.

### 상태 코드

| 상황 | 상태 | `code` |
|---|---|---|
| 정상 | 200 | — |
| 요청 본문이 스키마 위반(빈 문자열, 2000자 초과 등) | 400 | `INVALID_REQUEST` |
| 재시도 소진, DSL 검증 실패 | 422 | `DSL_VALIDATION_FAILED` |
| LLM 타임아웃 | 504 | `LLM_TIMEOUT` |
| LLM 공급자 오류(5xx, rate limit) | 502 | `LLM_UPSTREAM_ERROR` |
| 서버 내부 오류 | 500 | `INTERNAL_ERROR` |

Spring 쪽과의 계약: 모든 오류 응답은 위 네 필드 형태를 지킨다. FastAPI 기본 `{"detail": ...}` 형식이 새어 나가지 않도록 요청 검증 오류 핸들러를 따로 등록한다.

---

## 4. LLM 호출 흐름

### 프롬프트 구성

시스템 프롬프트는 세 덩어리로 만든다.

1. 역할 지시 — 암호화폐 백테스트 전략 번역기. 사용자 문장을 DSL JSON으로 옮기는 일만 한다.
2. DSL 스펙 — `Strategy` 모델의 JSON Schema를 `schema.py`에서 생성해 주입한다. 열거형 허용값과 수치 범위가 여기에 자동으로 포함되므로 프롬프트에 손으로 다시 적지 않는다.
3. 출력 규칙 — JSON 객체 하나만 출력. 설명·인사·코드블록 금지. 사용자가 명시하지 않은 값은 스펙 기본값을 쓰고, 기본값이 없는 필수 필드는 보수적으로 추론한다(리밸런싱 언급이 없으면 `WEEKLY`, 타임프레임 언급이 없으면 `1D`). **전략과 무관하거나 부실한 입력도 거부하지 않고 만든다.** 대신 임의로 정한 값을 `meta.assumptions`에 한 줄씩 적게 한다 (DECISIONS 결정 5).

사용자 프롬프트는 입력 문장을 그대로 감싼다. 입력 안에 지시문처럼 보이는 문장이 있어도 시스템 규칙을 덮지 못하도록, 사용자 입력은 구분된 블록 안에 넣는다.

### 실행 흐름

1. 요청 검증 통과. `translator`가 시스템·사용자 프롬프트를 만든다.
2. `llm_client`에 메시지를 넘겨 텍스트를 받는다. 타임아웃은 설정값(기본 30초).
3. `validator`가 텍스트를 JSON으로 파싱한다. 실패하면 파싱 오류 메시지를 만든다.
4. 파싱에 성공하면 `Strategy` 모델로 검증한다. 실패하면 필드 경로별 오류 목록을 만든다.
5. 3·4 중 실패가 있으면 재시도 프롬프트(직전 출력 + 오류 목록)를 붙여 2로 돌아간다.
6. 재시도 한도(기본 2회)에 닿으면 `DSLValidationError`를 던지고 마지막 오류 목록을 담아 422로 응답한다.
7. 성공하면 `Strategy`와 시도 횟수·모델명·소요 시간을 함께 반환한다.

### 재시도 정책

| 실패 종류 | 재시도 | 비고 |
|---|---|---|
| JSON 파싱 실패 | 한다 (최대 2회) | 오류 문구를 프롬프트에 붙인다 |
| 스키마 검증 실패 | 한다 (최대 2회) | 필드 경로별 오류를 붙인다 |
| LLM 타임아웃 | 하지 않는다 | 즉시 504. 대기 시간이 두 배로 늘어나는 걸 막는다 |
| LLM 429/5xx | 하지 않는다 (1차 구현) | 즉시 502. 지수 백오프는 필요해지면 그때 추가한다 |

재시도할 때는 이전 대화를 누적하지 않는다. 매번 시스템 프롬프트 + 원래 사용자 입력 + 직전 출력 + 오류만으로 새 요청을 만든다. 대화가 길어질수록 모델이 이전 실수를 반복하고, 토큰도 더 든다.

---

## 5. 에러 처리 규칙

- 예외는 발생 지점에서 도메인 예외로 번역하고, HTTP 변환은 `main.py`의 핸들러 한 곳에서만 한다. 라우터에서 `try/except`로 상태 코드를 만들지 않는다.
- 클라이언트에 스택 트레이스나 LLM 원문을 그대로 내보내지 않는다. `detail`에는 검증 오류 목록처럼 Spring 쪽이 화면에 쓸 수 있는 정보만 담는다.
- API 키와 프롬프트 전문은 로그와 응답 어디에도 나가지 않는다.
- 검증 오류 메시지는 두 벌을 만든다. LLM에게 주는 기술 문구(재시도용)와 클라이언트에 주는 한국어 문구(응답용). 같은 문자열을 양쪽에 쓰면 어느 한쪽은 반드시 부적절해진다.
- 예상하지 못한 예외는 500 `INTERNAL_ERROR`로 뭉뚱그리되, 서버 로그에는 요청 ID와 함께 전체 트레이스를 남긴다.
- 요청 ID는 Spring이 헤더로 넘겨주면 그대로 쓰고, 없으면 서버가 만든다. 응답 헤더에 되돌려준다.

---

## 6. 테스트 전략

### 계층별 방침

| 계층 | 대상 | 방식 |
|---|---|---|
| 스키마 | `core/schema.py` | 순수 단위 테스트, 네트워크 없음 |
| 검증기 | `services/validator.py` | 고정된 LLM 응답 문자열 입력 |
| 프롬프트 빌더 | `services/prompt_builder.py` | 자리표시자 치환 결과 확인 |
| 오케스트레이션 | `services/translator.py` | 가짜 `llm_client` 주입 |
| API | `api/v1/strategy.py` | FastAPI `TestClient` + 의존성 오버라이드 |

**실제 LLM API를 치는 테스트는 기본 스위트에 넣지 않는다.** `live` 표시를 달아 수동 실행으로 분리한다. CI가 과금되고 느려지고 불안정해지는 걸 막는다.

### 반드시 덮어야 할 케이스

- 스키마: 각 범위 제약의 경계값(`select_top` 0/1/30/31 등), 열거형 밖의 값 거부, 알 수 없는 필드 거부, 기본값 적용, 거부 층 교차 검증 규칙 전부, 경고 층 규칙이 거부가 아니라 `meta.warnings`로 나가는지.
- 검증기: 정상 JSON, 코드블록으로 감싸인 JSON, 앞뒤에 설명 문장이 붙은 응답, 깨진 JSON, 스키마 위반 JSON. 각 경우에 나오는 오류 문자열이 필드 경로를 포함하는지 확인한다.
- translator: 1회 성공, 1회 실패 후 2회차 성공, 3회 모두 실패(`DSLValidationError`), 타임아웃 즉시 전파. 가짜 LLM이 호출된 횟수를 세서 재시도 한도를 지켰는지 검증한다.
- API: 정상 200 응답 형태, 빈 입력 400, 검증 소진 422 응답 형태, 타임아웃 504. 오류 응답이 언제나 `code`/`message`/`detail` 형태인지 확인한다.

### 골든 케이스

자연어 입력 10~15개와 기대 DSL을 `tests/fixtures/golden_cases.json`에 쌓는다. 실전 LLM 스위트에서만 돌리고, 프롬프트를 고칠 때마다 수동으로 실행해 회귀를 본다. 완전 일치 대신 "검증을 통과하는가 + 핵심 필드 몇 개가 기대와 맞는가"로 판정한다. 완전 일치를 요구하면 프롬프트를 손댈 때마다 전부 깨진다.

### 커버리지 목표

`core/`와 `services/`에 대해 라인 커버리지 85% 이상. `llm_client.py`의 실제 HTTP 경로는 제외한다.

---

## 7. 구현 순서

각 단계는 완결된 커밋 단위다. 이전 단계가 테스트를 통과한 뒤에만 다음으로 넘어간다.

**1단계 — 뼈대 (0.5일)**
`requirements.txt`, `pyproject.toml`, `.env.example`, `.gitignore`, `app/main.py`, `app/api/v1/health.py`.
목표: 서버가 뜨고 `/health`가 200을 준다.

**2단계 — 설정 (0.5일)**
`app/core/config.py`.
목표: 필수 환경변수가 없으면 기동이 실패한다. 키 값이 로그에 찍히지 않는다.

**3단계 — DSL 스키마 (2일) ★ 가장 공들일 단계**
`app/core/schema.py`, `tests/test_schema.py`.
목표: §2의 모든 모델·제약·교차 검증이 정의되고 경계값 테스트가 전부 통과한다. 여기서 스펙을 잘못 잡으면 이후 전 단계가 흔들린다. 스펙이 교체된다는 전제가 있으니, 다른 파일이 개별 필드명을 참조하지 않는 구조인지 이 시점에 확인한다.

**4단계 — 오류 타입 (0.5일)**
`app/core/errors.py`, `app/core/logging.py`, `main.py`에 예외 핸들러 등록.
목표: 임의의 도메인 예외를 던지면 §3 표대로 상태 코드와 본문이 나온다.

**5단계 — 검증기 (1일)**
`app/services/validator.py`, `tests/test_validator.py`, `tests/fixtures/llm_responses.py`.
목표: 지저분한 LLM 응답 문자열을 넣어도 파싱·검증 결과와 오류 문자열이 기대대로 나온다. LLM은 아직 붙이지 않는다.

**6단계 — 프롬프트 (1.5일)**
`app/prompts/system.txt`, `user.txt`, `retry.txt`, `app/services/prompt_builder.py`, `tests/test_prompt_builder.py`.
목표: `schema.py`에서 뽑은 JSON Schema가 시스템 프롬프트에 주입되고, 재시도 프롬프트에 직전 출력과 오류가 들어간다.

**7단계 — LLM 클라이언트 (1일)**
`app/services/llm_client.py`.
목표: 메시지를 넣으면 텍스트가 나온다. 타임아웃과 HTTP 오류가 도메인 예외로 번역된다. 이 단계에서 처음으로 실제 API를 한 번 수동 호출해 본다.

**8단계 — 오케스트레이션 (1.5일)**
`app/services/translator.py`, `tests/test_translator.py`.
목표: 가짜 LLM으로 성공·재시도·소진·타임아웃 네 경로가 모두 검증된다. 호출 횟수가 한도를 지킨다.

**9단계 — API 연결 (1일)**
`app/api/v1/strategy.py`, `app/api/deps.py`, `tests/test_api_strategy.py`.
목표: `POST /api/v1/ai/strategies/translate`가 §3의 요청·응답 예시대로 동작한다.

**10단계 — 프롬프트 튜닝 (3일)**
`tests/fixtures/golden_cases.json`을 채우고 실전 LLM으로 돌리며 프롬프트를 고친다. 코드 수정은 최소로 하고 `.txt`만 손댄다. 재시도 발생률과 최종 실패율을 기록해 둔다.

**11단계 — 통합 (1.5일)**
Spring 쪽과 실제로 연결해 계약을 맞춘다. 오류 응답 형식, 요청 ID 전달, 타임아웃 값(Spring 쪽 타임아웃이 이 서버의 LLM 타임아웃에 재시도분을 더한 값보다 길어야 한다)을 조율한다. `README.md`에 실행법과 환경변수 목록을 적는다.

**12단계 — 정리 (1일)**
로깅 점검, 커버리지 확인, 배포 설정(Dockerfile 또는 실행 스크립트). 역할 2를 붙일 자리(`app/api/v1/score.py`, `app/services/scorer.py`)는 README에 메모만 남기고 파일은 만들지 않는다.

### 주차 배분

| 주차 | 내용 |
|---|---|
| 1주차 | 1~4단계 (뼈대, 설정, 스키마, 오류) |
| 2주차 | 5~7단계 (검증기, 프롬프트, LLM 클라이언트) |
| 3주차 | 8~9단계 (오케스트레이션, API) |
| 4주차 | 10단계 (프롬프트 튜닝) |
| 5주차 | 11~12단계 (통합, 정리) + 여유 |

---

## 8. 위험 요소

- **DSL 스펙 교체.** 3단계 이후 스펙이 바뀌면 `schema.py`와 프롬프트 `.txt`만 고치면 되어야 한다. 다른 파일에 필드명이 새어 나갔는지 6단계와 9단계 끝에 각각 점검한다.
- **프롬프트 튜닝 시간 초과.** 10단계에 3일을 잡았지만 실전에서는 더 걸리기 쉽다. 5주차 여유분을 여기에 쓴다.
- **LLM 응답 지연.** 재시도 때문에 사용자 대기 시간이 최대 세 배가 된다. 통합할 때 Spring 쪽 타임아웃 값을 반드시 맞춘다. 체감 지연이 문제가 되면 스트리밍이나 비동기 작업 큐를 검토하되, 1차 구현은 동기 호출로 간다.
- **역할 2 유입.** 이번 범위는 역할 1까지다. LightGBM 관련 파일을 미리 만들지 않는다.
