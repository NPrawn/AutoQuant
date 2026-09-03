# 인계 명세 — 스키마 상한 정리와 API 연결 (S-1·H-1·H-2·H-3)

작업 4건, 합계 14h. 배경은 [PLAN.md](PLAN.md), 도메인 결정의 근거는 [DECISIONS.md](DECISIONS.md), 전체 작업 분해는 [TASKS.md](TASKS.md)를 본다.

`S-1`은 [TASKS.md](TASKS.md)에 없는 정리 작업이라 이 문서에서 붙인 ID다. Jira `S15P21A107-115`에 해당한다.

## 티켓 제목

[`../../docs/git-convention.md`](../../docs/git-convention.md)의 `[영역] 대상과 작업 내용` 형식을 따른다. 영역은 `AI`다.

| 유형 | 제목 |
|---|---|
| 작업 | `[AI] DSL 스키마 잠정 상한 정리 - 명세 범위로 되돌리기` (S-1, 기존 `-115`) |
| 스토리 | `[AI] 전략 변환 API 엔드포인트와 남용 방어` (기존 `-124`) |
| 하위 작업 | `[AI] 설정과 translator 의존성 주입 구성` (H-1) |
| | `[AI] 전략 변환 엔드포인트 연결` (H-2) |
| | `[AI] 전략 변환 API 응답 계약 테스트` (H-3) |

작업 ID(H-1 등)는 제목에 넣지 않고 본문에 적는다. 정본이 제목 앞자리를 영역 태그에 쓰기 때문이다.

**`S15P21A107-120`을 먼저 닫는다.** `-124`와 같은 일을 가리키는 티켓이 둘이다. `-124`가 남용 방어까지 포함한 상위집합이라 그쪽을 남긴다.

## 이 구간의 성격

Epic G까지 develop에 들어갔다(`f5f5905`). `translate()`가 프롬프트 조립부터 검증·재시도까지 다 하는데 **밖에서 부를 방법이 없다.** 지금 이 서버는 `/health`만 응답한다.

임계 경로다. H가 끝나야 백엔드가 붙고, L-3 계약 산출물도 엔드포인트가 있어야 뽑힌다.

S-1을 앞에 두는 이유는 순서 때문이다. 상한을 고치면 검증 결과가 바뀌고 그게 H-3 테스트의 기대값이 된다. 반대로 하면 테스트를 두 번 쓴다.

## 작업 위치와 브랜치

작업 디렉토리는 저장소 `S15P21A107`의 `ai/`다.

**브랜치·커밋·MR 규칙은 [`../../docs/git-convention.md`](../../docs/git-convention.md)를 따른다.** 여기 옮겨 적지 않는다.

- 스토리 티켓을 만들면 Jira 자동화가 `develop`에서 브랜치를 만든다. 손으로 만들지 말고 `git fetch` 후 받아 쓴다
- 하위 작업마다 브랜치를 파지 않는다. 스토리 하나에 브랜치 하나, 하위 작업은 커밋 하나씩이다
- S-1은 작업 타입이라 자동 브랜치가 없다. `-124` 브랜치에 첫 커밋으로 얹는다. 파일이 겹치지 않고(`schema.py` 대 `api/`) 순서가 고정돼 있어 따로 뗄 이유가 없다

완료 보고에 커밋 SHA를 포함한다. SHA 없는 보고는 반려한다.

## 이미 있는 것

Epic A·C·B′·D·E·F·G가 끝나 있다. 새로 만들지 말고 가져다 쓴다.

| 쓸 것 | 어디 |
|---|---|
| 설정 객체 | `app/core/config.py`의 `settings` |
| 도메인 예외 4종 | `app/core/errors.py` |
| 예외 핸들러 등록 | `app/api/error_handlers.py`의 `register_exception_handlers()` |
| 요청 ID 미들웨어·구조화 로깅 | `app/main.py`, `app/core/logging.py` |
| 라우터 작성 형태 | `app/api/v1/health.py` |
| 오케스트레이션 | `app/services/translator.py`의 `translate()` |
| 요청·응답 모델 | `app/core/schema.py`의 `TranslateRequest`·`TranslateResponse`·`TranslateMeta`·`ErrorResponse` |

**`os.environ`을 읽는 파일은 `app/core/config.py` 하나뿐이다.** 다른 파일에서 직접 읽으면 반려한다.

**DSL 필드명·허용값을 `app/core/schema.py` 밖에서 문자열로 다시 적으면 반려한다.**

---

# S-1 스키마 잠정 상한 정리 (3h)

## 만들 것

`app/core/schema.py:10~17`의 상수 8개를 항목마다 판정하고, 남기는 것은 `DECISIONS.md`에 근거를 붙인다.

```
MAX_FILTER_DEPTH = 2      MAX_PERIOD_1H = 720
MAX_CONDITIONS = 5        MAX_PERIOD_4H = 540
MAX_GROUP_CHILDREN = 5    MAX_PERIOD_1D = 365
MAX_TOP_N = 30            MAX_USER_INPUT_LENGTH = 500
```

## 왜 지금 하나

**이 숫자들은 명세가 아니라 우리가 임의로 정한 값이다.** [Strategy 도메인 명세서](../../docs/Strategy%20도메인%20명세서.md)는 `topN`에 `> 0`만 걸고(597줄), 필터는 "GROUP / CONDITION 재귀 구조로 중첩 AND/OR를 지원한다"까지만 말한다(1202줄). 깊이 2단계도 조건 5개도 명세에 없다.

명세보다 좁게 잡아 두면 **명세상 유효한 전략을 우리가 거부한다.** 그 거부는 조용하지 않다 — 검증 실패로 잡혀 재시도 3회를 태우고 422로 나간다. GMS 크레딧을 세 번 쓰고 사용자는 "왜 안 되는지" 모르는 오류를 받는다.

## 항목마다 판정이 다르다

전부 되돌리라는 뜻이 아니다. 셋으로 갈린다.

| 갈래 | 해당 | 처리 |
|---|---|---|
| 방어선으로 남긴다 | `MAX_FILTER_DEPTH`, `MAX_CONDITIONS`, `MAX_GROUP_CHILDREN` | 남기되 근거를 적는다 |
| 명세로 되돌린다 | `MAX_TOP_N` | 명세가 `> 0`뿐이면 상한을 뗀다 |
| 다시 정한다 | `MAX_USER_INPUT_LENGTH` | 아래 참조 |
| 확인이 필요하다 | `MAX_PERIOD_*` | 아래 참조 |

**깊이·개수 상한을 남기는 근거는 비용이다.** 필터 트리가 깊어지면 프롬프트와 응답이 같이 커지고, 재귀 검증이 무거워지며, LLM이 틀릴 확률이 올라가 재시도가 붙는다. 이건 도메인 제약이 아니라 **운영 방어선**이라 명세와 무관하게 유지할 수 있다. 다만 그렇게 적어 둬야 한다 — 지금은 근거 없는 숫자와 구분이 안 된다.

**`MAX_USER_INPUT_LENGTH = 500`은 문서와 어긋난다.** [TASKS.md](TASKS.md)와 [PLAN.md](PLAN.md)는 `userInput`을 1~2000자로 적어 놨다. 어느 쪽이 맞는지 정하고 한쪽으로 맞춘다. 500자면 "여러 조건을 쌓는 요청"이 잘릴 수 있으니 `tests/fixtures/user_inputs.md`의 긴 발화로 재 보고 정한다.

**`MAX_PERIOD_*`는 캔들 개수 상한이다.** 명세서 261줄이 "Factor의 `period`는 Strategy의 `timeframe` 기준으로 해석한다"고만 말한다. 백테스트 엔진이 실제로 몇 캔들까지 데이터를 갖고 있는지가 진짜 상한이라, 이건 우리가 정할 값이 아니다. **전략팀이나 백엔드에 물어 확인한 값을 넣는다.** 답이 늦으면 현재 값을 유지하되 `DECISIONS.md`에 "미확인, 확인 후 교체"로 표시한다.

## 경고 층을 같이 채운다

`TranslateMeta.warnings`가 지금 `app/services/translator.py:45`에서 `[]`로 하드코딩돼 있다. 경고를 만드는 함수 자체가 없다.

[DECISIONS.md](DECISIONS.md)의 "검증을 세 층으로 나눈다"가 거부 층과 경고 층을 갈라 놨는데 경고 층이 통째로 비어 있다. 상한을 판정하다 보면 "막지는 않지만 이상하다"가 나오고, 그게 경고 층의 내용이다.

`Strategy`를 받아 문자열 목록을 돌려주는 함수를 `schema.py`에 만들고 `translate()`가 그 결과를 `meta.warnings`에 싣는다. 규칙은 상한 판정에서 나온 것으로 시작한다 — 처음부터 많이 만들지 않는다.

**경고를 검증 실패로 만들지 마라.** 경고는 200으로 나가고 재시도하지 않는다. 여기서 막으면 역할 2가 배울 사례가 사라진다.

## 완료 조건

- 상수 8개가 각각 "명세", "방어선", "미확인" 중 하나로 분류되고 `DECISIONS.md`에 근거가 남는다
- `MAX_USER_INPUT_LENGTH`가 문서와 코드에서 같은 값이다
- 경고 함수가 있고 `meta.warnings`가 하드코딩이 아니다
- 경고가 붙는 전략이 검증을 통과한다 — 실패로 바뀌면 반려
- `python -m pytest` 통과

---

# H-1 의존성 주입 (2h)

## 만들 것

`app/api/deps.py`. 설정과 `translate()`를 라우터에 주입하고, 테스트에서 갈아 끼울 수 있게 한다.

## 라우터가 직접 임포트하지 마라

`app/api/v1/strategy.py`가 `from app.services.translator import translate`를 직접 하면 테스트가 실제 LLM을 부르게 된다. FastAPI `Depends`로 받아 `app.dependency_overrides`로 바꿔 끼운다.

`app/services/llm_client.py`를 monkeypatch하는 방식으로 때우지 마라. 그건 서비스 내부 사정이라 구현이 바뀌면 테스트가 같이 깨진다.

## 예외에 재시도 정보를 싣는다

지금 `translate()`는 재시도가 소진되면 `DSLValidationError(format_user_errors(...))`로 문자열 하나만 던진다. **H-3이 요구하는 `detail.attempts`가 나올 수 없다.**

`DSLValidationError`가 시도 횟수와 마지막 오류 목록을 함께 갖도록 넓히고, `app/api/error_handlers.py`가 그걸 `detail`에 싣는다. 응답 형태는 `PLAN.md`의 실패 응답 예시를 따르되 **키 표기는 나머지 API와 맞춘다** — 문서에 `last_errors`로 적혀 있는데 다른 필드가 전부 camelCase다. 정하고 문서도 같이 고친다.

Pydantic `errors()` 원문을 그대로 실으면 내부 구조가 API로 새어 나간다. `format_user_errors()`를 거친 문자열만 싣는다.

## 완료 조건

- 라우터가 `translate`를 직접 임포트하지 않는다
- 테스트가 `dependency_overrides`로 가짜 translator를 끼울 수 있다
- `DSLValidationError`가 시도 횟수와 오류 목록을 갖는다
- 오류 응답 본문에 Pydantic 원문·스택 트레이스·API 키가 없다

---

# H-2 변환 엔드포인트 (4h)

## 만들 것

`app/api/v1/strategy.py`에 `POST /api/v1/ai/strategies/translate`. `app/main.py`에 라우터 등록.

## 라우터는 얇다

라우터가 하는 일은 셋이다. 요청 모델을 받고, 주입받은 translator를 부르고, `TranslateResponse`로 감싼다.

**분기를 라우터에 두지 마라.** 신규 생성과 수정을 가르는 것은 `currentStrategy`의 유무인데, 그 판단은 이미 `translate()` 안에 있다. 라우터에서 한 번 더 보면 정책이 두 군데로 흩어진다.

**재시도 소진 판정도 라우터에 두지 마라.** `translate()`가 `DSLValidationError`를 던지고 C-2 핸들러가 422로 바꾼다. 경로가 이미 이어져 있다.

`app/main.py`는 라우터 등록까지다. 비즈니스 로직이 들어가면 반려한다.

## 남용 방어 (`-124`의 나머지 절반)

이 엔드포인트는 요청 하나가 GMS 호출 최대 3회다. 막지 않으면 **크레딧이 요청 수만큼 빠진다.**

| 무엇을 | 어디서 |
|---|---|
| 입력 길이 | `TranslateRequest`의 `userInput` 상한 (S-1에서 확정한 값) |
| 시도 횟수 | `settings.max_retries` — 이미 있다. 요청이 늘릴 수 없어야 한다 |
| 타임아웃 | `settings.llm_timeout_seconds` — 이미 있다 |
| 동시 요청 | 아래 참조 |

**요청 본문이 재시도 횟수나 모델을 지정할 수 있게 만들지 마라.** 지금 `TranslateRequest`에 그런 필드가 없고, 앞으로도 없어야 한다. 있으면 호출자가 비용을 정하게 된다.

**동시 요청 제한은 이번에 넣지 않는다.** 이 서버는 Spring만 부르고 외부에 노출되지 않는다([`../CLAUDE.md`](../CLAUDE.md)의 "안 하는 것" — `--host 0.0.0.0`을 붙이지 않는 이유가 같다). 인증도 `app/main.py:40`에 TODO로 남아 있어 백엔드 계약 뒤에 붙인다. **지금 필요한 방어는 "한 요청이 얼마나 쓸 수 있나"까지고, "몇 요청까지 받나"는 배포 구간(L-5)의 문제다.** 이 판단을 `DECISIONS.md`에 남긴다 — 안 적으면 다음 사람이 빠뜨린 줄 안다.

## `changes`가 신규 생성을 표시하지 않는다

`describe_changes(None, ...)`가 빈 목록을 돌려준다. [TASKS.md](TASKS.md) G-6 케이스 1은 "`changes`에 신규 생성 표시"를 요구하는데 `tests/test_translator.py`가 `== []`로 못 박아 놨다. **둘 중 하나는 틀렸고, 응답 계약을 정하는 지금이 정할 때다.**

프론트 입장에서 "새로 만들었다"와 "바꿀 게 없었다"가 같은 빈 목록으로 오면 구분이 안 된다. 어느 쪽으로 정하든 코드·테스트·TASKS를 같이 맞춘다.

## 완료 조건

- `POST /api/v1/ai/strategies/translate`가 200을 준다
- 라우터에 신규·수정 분기와 소진 판정이 없다
- `app/main.py`에 비즈니스 로직이 없다
- 요청 본문이 재시도 횟수·모델·타임아웃을 지정할 수 없다
- `uvicorn app.main:app` 기동 후 `/health/ready` 200

---

# H-3 API 테스트 (4h)

## 덮어야 할 케이스

[TASKS.md](TASKS.md) H-3 그대로다.

1. `currentStrategy`가 null — 200, 전체 전략 생성
2. `currentStrategy`가 있음 — 200, `meta.changes`에 바뀐 것만
3. `userInput`이 빈 문자열 — 400 `INVALID_REQUEST`
4. `userInput`이 상한 + 1자 — 400 `INVALID_REQUEST`
5. 검증 소진 — 422 `DSL_VALIDATION_FAILED`, `detail`의 시도 횟수가 3
6. 타임아웃 504, 공급자 오류 502
7. 모든 오류 응답이 `code`·`message`·`detail` 세 필드를 가짐

## 실제 API를 치지 마라

`tests/`는 GMS 키가 없는 환경에서 통과해야 한다. 실전 호출은 L-1의 `test_live.py`로 따로 뺀다.

`tests/fixtures/llm_responses.py`에 이미 응답 표본이 있다. 새로 만들지 말고 가져다 쓴다.

## 상태 코드만 보지 마라

`assert response.status_code == 422` 하나로 끝내면 3번과 4번이 같은 테스트가 된다. **`code` 문자열까지 본다.** 그게 Spring이 분기하는 값이라 계약의 실체다.

## 완료 조건

- 위 7건 통과
- `python -m pytest` 전체 통과. GMS 키 없는 환경에서도 통과
- 오류 응답 본문에 스택 트레이스와 API 키가 없다

---

# 반려 사유 정리

1. `app/core/config.py` 밖에서 `os.environ`을 읽음
2. DSL 필드명·허용값을 `app/core/schema.py` 밖에서 문자열로 다시 적음
3. 라우터가 `translate`를 직접 임포트해 테스트에서 갈아 끼울 수 없음
4. 라우터나 `main.py`에 비즈니스 로직이 들어감
5. 테스트가 실제 LLM API를 호출함
6. 경고 층 규칙을 검증 실패로 만듦
7. 상한을 남기면서 근거를 `DECISIONS.md`에 안 적음
8. 완료 보고에 커밋 SHA가 없음

# 이 구간에서 하지 않는 것

| 안 하는 것 | 언제 |
|---|---|
| 프롬프트 도메인 규칙 | K-3 (`-116`) |
| 골든 케이스 정답지 | K-4 (`-116`) |
| Spring-to-AI 인증 | 백엔드 계약 후 |
| 동시 요청 제한 | L-5 배포 구간 |
| 계약 산출물 내보내기 | L-3 |

# 열려 있는 질문

| 질문 | 누구에게 | 막는 것 |
|---|---|---|
| `MAX_PERIOD_*`의 실제 상한 — 엔진이 몇 캔들까지 갖고 있나 | 전략팀·백엔드 | S-1 (미확인으로 두고 진행 가능) |
| 오류 `detail`의 키 표기 | 백엔드 | H-1 |
| 신규 생성 시 `changes`를 어떻게 표시할지 | 프론트 | H-2 |
