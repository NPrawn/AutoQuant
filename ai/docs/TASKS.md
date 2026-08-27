# 작업 분해 (Engineer 배정용)

[PLAN.md](PLAN.md)를 배정 가능한 최소 단위로 쪼갠 것. 각 항목은 하나의 커밋, 하나의 완료 보고 단위다.

## 전제 — 순서를 뒤집은 이유

**전략 도메인은 전략팀이 정하고, 명세가 1~2주 안에 온다.** 그전까지 우리가 쓰는 DSL은 임시안이다([DECISIONS.md](DECISIONS.md) 참조).

그래서 원래 계획(스키마 먼저, 인프라 나중)을 뒤집는다. **명세가 와도 안 바뀌는 것부터 만들고, 바뀔 것은 최소한만 만들어 돌려본다.**

| 구분 | 명세 도착 시 |
|---|---|
| 프로젝트 기반, 설정, 오류 처리, 로깅 | 안 바뀜 |
| LLM 클라이언트, 재시도 오케스트레이션 | 안 바뀜 |
| 검증기 (스키마 객체를 주입받는 구조) | 안 바뀜 |
| 프롬프트 로더 + 스키마 자동 주입 | 안 바뀜 |
| API 껍데기, 누적 편집 구조 | 안 바뀜 |
| **`schema.py` 내용** | **다시 씀** |
| **프롬프트 본문의 도메인 규칙** | **다시 씀** |
| **골든 케이스의 기대 DSL** | **다시 씀** |
| **프롬프트 튜닝** | **다시 함** |

임시 스키마에 시간을 더 쓰지 않는다. 이미 정한 것을 그대로 옮기고 넘어간다.

## 공통 규칙

- 브랜치: `feat/ai-server/<task-id>`. `main` 직접 푸시 금지.
- 완료 보고에 커밋 SHA 포함. SHA 없는 보고는 반려한다.
- **DSL 필드명·허용값을 `app/core/schema.py` 밖에서 문자열로 다시 적으면 반려한다.** 명세 교체가 예정돼 있어 이 원칙이 전보다 중요하다.
- 각 작업의 완료 조건에 적힌 명령이 실제로 통과해야 완료다.

**상태 표기:** ⬜ 대기 / 🟦 진행 / ✅ 완료 / 🚫 차단

---

# Phase 1 — 명세 독립 인프라 (1~2주차)

## Epic A. 기반 (2일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| A-1 | 프로젝트 초기화 | `requirements.txt`, `pyproject.toml`, `.gitignore`, 패키지 디렉토리와 `__init__.py` 일체 | — | `pip install -r requirements.txt` 성공, `python -c "import app"` 성공 |
| A-2 | 설정 객체 | `app/core/config.py`, `.env.example` | A-1 | 필수 환경변수 누락 시 임포트 단계에서 예외. 설정 객체를 출력해도 API 키가 마스킹됨 |
| A-3 | 앱 부트 + 헬스 | `app/main.py`, `app/api/v1/health.py`, `app/api/__init__.py`, `app/api/v1/__init__.py` | A-2 | `uvicorn app.main:app` 기동, `GET /health` 200 |

**A-2 환경변수** — `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`, `LLM_TIMEOUT_SECONDS`(기본 30), `MAX_RETRIES`(기본 2), `LOG_LEVEL`(기본 INFO), `APP_ENV`. `os.environ`을 읽는 유일한 파일이다.

**A-3** — `/health/ready`는 설정 로드와 프롬프트 파일 존재만 확인한다. LLM API를 호출하지 않는다. 프롬프트 파일이 없는 시점이므로 `503`이 정상이고, E-2 완료 후 200이 되는지 다시 본다.

## Epic C. 오류 처리와 로깅 (1.5일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| C-1 | 예외 계층 | `app/core/errors.py` | A-1 | 예외 4종이 각각 HTTP 상태 코드와 오류 코드 문자열을 속성으로 가짐 |
| C-2 | 예외 핸들러 등록 | `app/main.py` | C-1 | 도메인 예외를 던지면 `code`/`message`/`detail` 형태로 응답 |
| C-3 | 요청 검증 오류 오버라이드 | `app/main.py` | C-2 | 잘못된 요청 본문에 FastAPI 기본 `{"detail": ...}`가 아니라 위 형태로 400 응답 |
| C-4 | 요청 ID + 구조화 로깅 | `app/core/logging.py`, `app/main.py` | C-2 | 요청 헤더의 요청 ID를 이어받고 없으면 생성, 응답 헤더로 반환. 로그에 요청 ID 포함. API 키 미노출 |
| C-5 | 오류 응답 테스트 | `tests/test_errors.py` | C-4 | 예외 4종이 상태 코드 표대로 응답. 응답 본문에 스택 트레이스·API 키 부재 |

**C-1 예외 목록**

| 예외 | 상태 | 코드 |
|---|---|---|
| `LLMTimeoutError` | 504 | `LLM_TIMEOUT` |
| `LLMUpstreamError` | 502 | `LLM_UPSTREAM_ERROR` |
| `DSLValidationError` | 422 | `DSL_VALIDATION_FAILED` |
| `PromptTooLongError` | 400 | `INVALID_REQUEST` |

그 밖의 예외는 500 `INTERNAL_ERROR`.

## Epic F. LLM 클라이언트 (1.5일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| F-1 | 클라이언트 인터페이스 + 구현 | `app/services/llm_client.py` | A-2 | 메시지 목록과 타임아웃을 받아 응답 텍스트와 토큰 수를 반환하는 함수 하나 |
| F-2 | 오류 번역 | `app/services/llm_client.py` | F-1, C-1 | 타임아웃은 `LLMTimeoutError`, 429·5xx·연결 실패는 `LLMUpstreamError`. 공급자 예외가 밖으로 새지 않음 |
| F-3 | 수동 스모크 | — | F-2 | 실제 API를 한 번 호출해 텍스트가 돌아옴을 확인. 결과를 완료 보고에 붙임 |

**F-1 주의** — 공급자를 갈아 끼우는 지점이다. 함수 시그니처에 특정 공급자의 타입이 새어 나가면 반려한다. GMS 게이트웨이를 `openai` SDK의 `base_url`로 물린다([DECISIONS.md](DECISIONS.md) 결정 4). 모델 교체는 `LLM_MODEL` 한 줄, 공급자 교체는 `LLM_BASE_URL`까지 두 줄이어야 한다.

**F-3에서 확인할 것** — `response_format`의 `json_schema`가 GMS 프록시를 통과하는지 실제로 찍는다. 통과하면 E-3에서 B-5의 JSON Schema를 그대로 꽂고 D-1은 fallback으로 내린다. 막히면 프롬프트로만 강제하고 D-1이 본역할이다. 결과를 결정 4 확정 근거로 DECISIONS.md에 남긴다.

## Epic B′. 임시 스키마 (1일) — 명세 도착 시 교체 대상

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| B-1 | 열거형 + 하위 모델 | `app/core/schema.py` | A-1 | 아래 임시 DSL 스펙대로 정의 |
| B-2 | 루트 모델 + 거부 규칙 | `app/core/schema.py` | B-1 | `Strategy` 정의. 거부 층 규칙 동작. 모든 모델이 알 수 없는 필드 거부 |
| B-3 | 경고 규칙 | `app/core/schema.py` | B-2 | 경고 층 규칙을 평가해 문자열 목록을 반환하는 함수. 검증 실패로 만들지 않음 |
| B-4 | API 모델 | `app/core/schema.py` | B-2 | `TranslateRequest`, `TranslateResponse`, `TranslateMeta`, `ErrorResponse` |
| B-5 | 프롬프트용 JSON Schema 생성기 | `app/core/schema.py` | B-2 | `Strategy`의 JSON Schema를 문자열로 반환하는 함수. 열거형 허용값과 수치 범위가 결과에 포함됨 |
| B-6 | 스키마 테스트 | `tests/test_schema.py`, `tests/conftest.py` | B-5 | 경계값·거부·경고 케이스 통과 |

**시간을 더 쓰지 않는다.** 아래 스펙을 그대로 옮기고 넘어간다. 어차피 교체된다.

### 임시 DSL 스펙

**열거형**

| 이름 | 값 |
|---|---|
| `SignalType` | `CROSS_SECTIONAL_RANK` |
| `FeatureType` | `RETURN`, `RSI`, `SMA`, `EMA`, `VOLUME` |
| `Operator` | `LT`, `GT` |
| `PortfolioType` | `EQUAL_WEIGHT`, `RANK_WEIGHTED` |
| `Rebalance` | `DAILY`, `WEEKLY`, `MONTHLY` |
| `Timeframe` | `1D`, `4H` |

**모델**

| 모델 | 필드 | 타입·제약 | 기본값 |
|---|---|---|---|
| `Universe` | `market` | `"UPBIT_KRW"` 고정 | `UPBIT_KRW` |
| | `top_by_trading_value` | int, 5~30 | 20 |
| `Feature` | `type` | `FeatureType` | 없음 |
| | `lookback_days` | int, 1~365 | 지표별 관행값 |
| `FilterCondition` | `feature` | `Feature` | 없음 |
| | `operator` | `Operator` | 없음 |
| | `threshold` | float 또는 null | null |
| | `compare_feature` | `Feature` 또는 null | null |
| `Signal` | `type` | `SignalType` | `CROSS_SECTIONAL_RANK` |
| | `feature` | `Feature` | 없음 |
| | `select_top` | int, 1~30 | 5 |
| | `filters` | `FilterCondition` 목록, 0~3개 | 빈 목록 |
| `Portfolio` | `type` | `PortfolioType` | `EQUAL_WEIGHT` |
| | `weight_decay` | float 0~1 또는 null | null |
| | `max_asset_weight` | float, 0 초과 1 이하 | 0.34 |
| `Risk` | `stop_loss` | float 또는 null, -1~0 | null |
| | `take_profit` | float 또는 null, 0~10 | null |
| | `regime_gate` | bool | false |
| `Execution` | `rebalance` | `Rebalance` | `WEEKLY` |
| | `timeframe` | `Timeframe` | `1D` |
| `Strategy` | `universe`, `signal`, `portfolio`, `risk`, `execution` | 각 하위 모델 | 없음 |

**거부 규칙** (검증 실패 후 재시도)

1. 범위 밖 숫자, 열거형 밖의 값, 필수 필드 누락
2. 알 수 없는 필드
3. `signal.select_top`이 `universe.top_by_trading_value`보다 큼
4. `FilterCondition`의 `threshold`와 `compare_feature` 중 정확히 하나만 채워지지 않음
5. `portfolio.weight_decay`가 `type`이 `RANK_WEIGHTED`가 아닌데 채워짐

**경고 규칙** (통과시키되 `meta.warnings`에 담음)

1. `signal.feature.lookback_days`가 리밸런싱 주기의 5배 미만
2. `portfolio.max_asset_weight` 곱하기 `signal.select_top`이 1.0 미만
3. `stop_loss`가 -0.03보다 얕음 (잦은 손절 우려)

**API 모델**

| 모델 | 필드 |
|---|---|
| `TranslateRequest` | `text`(str, 1~2000자), `current_strategy`(`Strategy` 또는 null) |
| `TranslateResponse` | `strategy`(`Strategy`), `changes`(문자열 목록), `meta`(`TranslateMeta`) |
| `TranslateMeta` | `attempts`(int), `model`(str), `elapsed_ms`(int), `warnings`(문자열 목록) |
| `ErrorResponse` | `code`(str), `message`(str), `detail`(object 또는 null) |

`TranslateMeta`에 `assumptions`(문자열 목록)를 넣는다. 결정 5가 "무조건 전략 생성"으로 확정돼([DECISIONS.md](DECISIONS.md)) 입력이 부실해도 서버가 기본값으로 채우기 때문에, **무엇을 임의로 정했는지 알리는 유일한 통로가 이 필드다.** 비워 두면 사용자는 자기가 말하지 않은 값이 들어간 줄 모른다.

**B-6이 덮어야 할 케이스**

1. 각 수치 필드의 경계값 — 하한보다 1 작은 값, 하한, 상한, 상한보다 1 큰 값
2. 열거형 밖의 값 거부 — 6개 열거형 각각
3. 알 수 없는 필드 거부 — 각 모델 1건
4. 기본값 적용 확인
5. 거부 규칙 3~5 각각
6. 경고 규칙 1~3 각각 — **검증은 통과하고 경고 문자열이 나오는지** 확인
7. `filters` 0개 통과 / 3개 통과 / 4개 거부
8. 정상 전략 1건 통과

## Epic D. 검증기 (2일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| D-1 | 응답 텍스트 정제 | `app/services/validator.py` | A-1 | 코드 펜스, 앞뒤 설명 문장에서 JSON 본문만 추출 |
| D-2 | 파싱 + 스키마 검증 | `app/services/validator.py` | D-1, B-2 | 성공 시 `Strategy`, 실패 시 오류 목록을 담은 결과 객체 반환. 예외를 밖으로 던지지 않음 |
| D-3 | 오류 문자열 2벌 생성 | `app/services/validator.py` | D-2 | 재시도용 문구와 사용자 응답용 한국어 문구를 각각 생성. 둘 다 필드 경로 포함 |
| D-4 | 검증기 테스트 | `tests/test_validator.py`, `tests/fixtures/llm_responses.py` | D-3 | 아래 케이스 통과 |

**D-1 주의** — 정제를 관대하게 만들수록 LLM의 형식 위반을 눈감아 준다. 코드 펜스 제거와 첫 여는 중괄호부터 마지막 닫는 중괄호까지 잘라내기, 두 가지까지만 한다. 그 이상은 프롬프트로 잡는다. F-3에서 구조화 출력이 통과하면 이 작업은 fallback으로 내려간다. 그래도 지운다는 뜻은 아니다.

**D-4 입력 케이스**

1. 순수 JSON 객체 — 성공
2. 코드 펜스로 감싸인 JSON — 성공
3. "네, 다음과 같습니다:" 뒤에 JSON, 그 뒤에 안내 문장 — 성공
4. 중괄호가 닫히지 않은 문자열 — 파싱 실패
5. 범위 위반 값 — 검증 실패, 오류 문구에 필드 경로 포함
6. 스펙에 없는 필드 — 검증 실패, 오류 문구에 해당 경로 포함
7. 빈 문자열 — 파싱 실패
8. JSON 배열(객체가 아님) — 검증 실패

**D-2 주의** — 검증기는 스키마 타입을 **주입받는다.** `Strategy`를 직접 임포트해 하드코딩하면 명세 교체 시 이 파일도 고쳐야 한다. 반려 사유다.

## Epic E. 프롬프트 (2일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| E-1 | 프롬프트 로더 | `app/services/prompt_builder.py` | A-2 | 기동 시 `app/prompts` 아래 파일을 읽어 캐시. 파일 부재 시 기동 실패 |
| E-2 | 프롬프트 초안 | `app/prompts/system.txt`, `create.txt`, `edit.txt`, `retry.txt` | E-1 | 자리표시자 규약대로 작성. `/health/ready` 200 |
| E-3 | 시스템 프롬프트 조립 | `app/services/prompt_builder.py` | E-2, B-5 | 스키마 자리표시자에 B-5의 JSON Schema가 주입됨 |
| E-4 | 수정·재시도 프롬프트 조립 | `app/services/prompt_builder.py` | E-3, D-3 | 현재 전략, 직전 출력, 검증 오류가 주입됨 |
| E-5 | 프롬프트 빌더 테스트 | `tests/test_prompt_builder.py` | E-4 | 치환 후 자리표시자 잔재가 없음. 스키마의 열거형 값이 실제로 포함됨 |

**자리표시자 규약** — `dsl_schema`, `user_input`, `current_strategy`, `previous_output`, `validation_errors`. 각각 이중 중괄호로 감싼다. 이 5개 외에는 쓰지 않는다.

**프롬프트 파일 4종의 역할**

| 파일 | 언제 쓰나 |
|---|---|
| `system.txt` | 항상. 역할 지시 + 스키마 주입 + 출력 규칙 |
| `create.txt` | `current_strategy`가 null일 때. 처음부터 만든다 |
| `edit.txt` | `current_strategy`가 있을 때. 무엇을 바꿀지만 판단한다 |
| `retry.txt` | 검증 실패 후. 직전 출력과 오류를 붙인다 |

**E-2 주의** — 도메인 규칙(기본값 정책, 지표별 관행값 등)은 명세 도착 후 다시 쓴다. 지금은 구조가 돌아가는 최소한만 적는다.

**E-2 필수 지시 (결정 5)** — 입력이 전략 설명이 아니거나 내용이 부실해도 **거부하지 않고 전략을 만든다.** 대신 임의로 정한 값을 `assumptions`에 한 줄씩 적게 한다. "돈 많이 버는 전략" 같은 입력에 전부 기본값을 채웠다면 그 사실이 사용자에게 전달돼야 한다.

## Epic G. 오케스트레이션 (2일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| G-1 | 신규 생성 경로 | `app/services/translator.py` | E-3, F-2, D-2 | 가짜 LLM이 정상 JSON을 주면 `Strategy` 반환 |
| G-2 | 수정 경로 | `app/services/translator.py` | G-1, E-4 | `current_strategy`가 있으면 `edit.txt` 경로로 간다 |
| G-3 | 재시도 루프 | `app/services/translator.py` | G-2 | 검증 실패 시 재시도. `MAX_RETRIES` 초과 시 `DSLValidationError` |
| G-4 | 실패 즉시 전파 | `app/services/translator.py` | G-3 | 타임아웃과 공급자 오류는 재시도하지 않고 그대로 올라감 |
| G-5 | changes와 meta 수집 | `app/services/translator.py` | G-4 | 이전 전략과 새 전략을 비교해 `changes` 생성. `attempts`, `model`, `elapsed_ms`, `warnings` 반환 |
| G-6 | 오케스트레이션 테스트 | `tests/test_translator.py` | G-5 | 아래 경로 통과 |

**G-6 경로**

1. 신규 1회 성공 — `attempts`가 1, 호출 1회, `changes`에 신규 생성 표시
2. 수정 1회 성공 — `edit.txt`가 쓰였는지, `changes`에 바뀐 필드만 담겼는지
3. 1회 검증 실패 후 2회차 성공 — 호출 2회, 2회차 프롬프트에 1회차 오류 포함
4. 3회 모두 실패 — `DSLValidationError`, 호출 3회(4회 아님), 마지막 오류 목록이 예외에 담김
5. 1회차 타임아웃 — `LLMTimeoutError`, 호출 1회
6. 경고가 있는 전략 — 200 반환되고 `warnings`에 담김

**G-3 주의** — 재시도 시 대화를 누적하지 않는다. 매 시도는 시스템 프롬프트, 원래 입력, 직전 출력, 오류로 구성된 새 요청이다.

**G-5 changes 생성** — LLM에게 만들게 하지 말고 **두 `Strategy` 객체를 비교해 서버가 생성한다.** LLM이 바꿨다고 말한 것과 실제로 바뀐 것이 다를 수 있다.

## Epic H. API 연결 (1.5일)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| H-1 | 의존성 주입 | `app/api/deps.py` | G-5 | 설정과 translator를 주입. 테스트에서 오버라이드 가능 |
| H-2 | 변환 엔드포인트 | `app/api/v1/strategy.py`, `app/main.py` | H-1, B-4 | `POST /api/v1/ai/strategy/translate` 동작 |
| H-3 | API 테스트 | `tests/test_api_strategy.py` | H-2 | 아래 케이스 통과 |

**H-3 케이스**

1. `current_strategy`가 null — 200, 전체 전략 생성
2. `current_strategy`가 있음 — 200, `changes`에 바뀐 것만
3. `text`가 빈 문자열 — 400 `INVALID_REQUEST`
4. `text`가 2001자 — 400 `INVALID_REQUEST`
5. 검증 소진 — 422 `DSL_VALIDATION_FAILED`, `detail.attempts`가 3
6. 타임아웃 504, 공급자 오류 502
7. 모든 오류 응답이 `code`, `message`, `detail` 세 필드를 가짐

## Epic J. 자연어 입력 수집 (1일, 병렬 가능)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| J-1 | 사용자 발화 수집 | `tests/fixtures/user_inputs.md` | — | 전략을 요청하는 자연어 문장 30~50건. 난이도·유형별 분류 |

**명세와 무관한 작업이라 지금 바로 할 수 있다.** 기대 DSL은 명세 도착 후 채운다.

수집 대상: 단순 랭킹 요청, 조건이 붙은 요청, 여러 조건을 쌓는 요청, 모호한 요청, 전략이 아닌 입력, 지원 범위 밖 요청(진입·청산 신호형 등).

**이 목록 자체가 전략팀에 넘길 자료가 된다.** 사용자가 실제로 무엇을 어떻게 말하는지 알고 도메인을 설계하는 것과 모르고 하는 것은 다르다.

---

# Phase 2 — 명세 교체 (명세 도착 직후, 2~3일)

| ID | 작업 | 파일 | 완료 조건 |
|---|---|---|---|
| K-1 | 스키마 교체 | `app/core/schema.py` | 전략팀 명세대로 재정의. 다른 파일 수정 없이 테스트가 도는지 확인 |
| K-2 | 스키마 테스트 교체 | `tests/test_schema.py` | 새 스펙 기준 경계값·거부·경고 케이스 |
| K-3 | 프롬프트 도메인 규칙 작성 | `app/prompts` 아래 파일 | 기본값 정책, 지표별 관행값, 지원 범위 밖 요청 처리 |
| K-4 | 기대 DSL 채우기 | `tests/fixtures/golden_cases.json` | J-1의 입력에 새 스펙 기준 기대 결과를 붙임 |

**K-1의 진짜 완료 조건은 "다른 파일을 안 고쳤다"이다.** 고쳐야 했다면 Phase 1에서 원칙을 어긴 것이고, 그 파일을 함께 리팩터링한다.

---

# Phase 3 — 튜닝·통합 (3~5주차)

| ID | 작업 | 파일 | 선행 | 완료 조건 |
|---|---|---|---|---|
| L-1 | 실전 테스트 러너 | `tests/test_live.py`, `pyproject.toml` | K-4 | live 표시가 붙은 테스트만 따로 실행됨. 기본 스위트에서 제외 |
| L-2 | 프롬프트 튜닝 | `app/prompts` 아래 파일 | L-1 | 회차별 기록. 목표는 통과율 90% 이상, 평균 시도 1.3회 이하 |
| L-3 | 계약 산출물 내보내기 | `scripts/export_contract.py`, `docs/contract/` | H-3 | OpenAPI 명세, DSL JSON Schema, 예시 5건이 코드에서 자동 생성 |
| L-4 | Spring 통합 | `README.md` | L-3 | 실제 호출 성공. 오류 형식·요청 ID·타임아웃 합의를 README에 기록 |
| L-5 | 배포 설정 | `Dockerfile`, `.dockerignore` | L-4 | 컨테이너에서 기동, `/health` 200 |
| L-6 | 마무리 | `README.md` | L-5 | 환경변수 목록, 실행법, 커버리지 확인, 역할 2 확장 지점 메모 |

**L-2 측정** — 회차마다 골든 케이스를 돌리고 표를 남긴다. 통과 건수, 실패 원인, 평균 시도 횟수, 응답 시간 중앙값과 상위 95%. 기록 없이 좋아졌다는 보고는 반려한다. 응답 시간은 Spring 타임아웃을 정하는 근거라 L-4에 넘긴다.

**L-3** — 백엔드가 우리 없이 착수할 수 있게 하는 산출물이다. 손으로 쓰지 않고 스크립트로 뽑는다. 성격은 확정 명세가 아니라 **제안**이다.

---

## 의존 순서

```
A-1 → A-2 → A-3
A-1 → C-1 → C-2 → C-3 → C-4 → C-5
A-2 → F-1 → F-2 → F-3
A-1 → B-1 → B-2 → B-3 → B-4 → B-5 → B-6
B-2 → D-1 → D-2 → D-3 → D-4
A-2 → E-1 → E-2 → E-3 → E-4 → E-5        (E-3은 B-5, E-4는 D-3 필요)
E-3, F-2, D-2 → G-1 → G-2 → G-3 → G-4 → G-5 → G-6
G-5 → H-1 → H-2 → H-3
J-1 은 언제든 병렬

(명세 도착) → K-1 → K-2, K-3 → K-4 → L-1 → L-2
H-3 → L-3 → L-4 → L-5 → L-6
```

**임계 경로** — A-1 → B-2 → D-2 → G-1 → H-2 → (명세 도착) → K-1 → L-2

**병렬 가능** — C(오류), F(LLM 클라이언트), J(입력 수집)는 A-2 이후 서로 독립이다. 1인 작업이므로 순서대로 하되, 막히면 J로 넘어간다.

## 일정

| 주차 | 내용 |
|---|---|
| 1주차 | A, C, F |
| 2주차 | B′, D, E + J 병렬 |
| 3주차 | G, H — 이 무렵 명세 도착 예상 |
| 4주차 | K(교체), L-1, L-2 시작 |
| 5주차 | L-2 마무리, L-3부터 L-6까지 |

**여유가 거의 없다.** 필터와 누적 편집이 추가되면서 5일쯤 늘었다. 명세 도착이 늦어지거나 튜닝이 길어지면 L-5(배포)와 L-6(마무리)부터 밀린다.

## 미결로 남아 배정 전 확인이 필요한 것

| 항목 | 영향받는 작업 |
|---|---|
| 결정 6 — 미니 백테스터 포함 여부 | 전체 일정 |

결정 4·5는 2026-08-27 확정됐다. [DECISIONS.md](DECISIONS.md) 확정 로그 참조.
