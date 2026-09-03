# 인계 명세 — 스키마 상한 정리와 API 연결 (S-1·H-1·H-2·S-2·H-3)

작업 5건, 합계 20h. 배경은 [PLAN.md](PLAN.md), 도메인 결정의 근거는 [DECISIONS.md](DECISIONS.md), 전체 작업 분해는 [TASKS.md](TASKS.md)를 본다.

`S-1`은 [TASKS.md](TASKS.md)에 없는 정리 작업이라 이 문서에서 붙인 ID다. Jira `S15P21A107-115`에 해당한다.

## 티켓 제목

[`../../docs/git-convention.md`](../../docs/git-convention.md)의 `[영역] 대상과 작업 내용` 형식을 따른다. 영역은 `AI`다.

| 유형 | 제목 |
|---|---|
| 작업 | `[AI] DSL 스키마 잠정 상한 정리 - 명세 범위로 되돌리기` (S-1, 기존 `-115`) |
| 스토리 | `[AI] 전략 변환 API 엔드포인트와 남용 방어` (기존 `-124`) |
| 하위 작업 | `[AI] 설정과 translator 의존성 주입 구성` (H-1, 3h) |
| | `[AI] 전략 변환 엔드포인트 연결` (H-2, 5h) |
| | `[AI] 남용 방어 - 짧은 경로와 총량 차단` (S-2, 4h) |
| | `[AI] 전략 변환 API 응답 계약 테스트` (H-3, 5h) |

작업 ID(H-1 등)는 제목에 넣지 않고 본문에 적는다. 정본이 제목 앞자리를 영역 태그에 쓰기 때문이다. `S-1`·`S-2`는 [TASKS.md](TASKS.md)에 없어 이 문서에서 붙인 ID다.

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
- S-1은 작업 타입이라 자동 브랜치가 없다. 티켓을 진행 중으로 옮기면 생기는 브랜치를 쓰고, 없으면 `-124` 브랜치에 첫 커밋으로 얹는다. 파일이 겹치지 않고(`schema.py` 대 `api/`) 순서가 고정돼 있어 따로 뗄 이유가 없다

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

`app/core/schema.py`의 잠정 상한 여섯 개를 걷어내고 하나만 남긴다. `tests/test_schema.py`에서 대응 케이스를 정리한다.

```
제거: MAX_CONDITIONS  MAX_GROUP_CHILDREN  MAX_TOP_N
      MAX_PERIOD_1H   MAX_PERIOD_4H       MAX_PERIOD_1D

남김: MAX_FILTER_DEPTH = 5   # 파서 방어선. 도메인 규칙이 아니다 (2 → 5)
```

## 왜 걷어내나

이 숫자들은 [-90](https://ssafy.atlassian.net/browse/S15P21A107-90)에서 모르는 값을 보수적으로 잡은 것이다. 그 뒤 사실 셋이 드러났다.

**백엔드에도 상한이 없다.** `backend/api`의 `GroupFilterNode`는 `@NotEmpty`뿐이고 `FactorOperand`는 `period > 0`만 본다. [Strategy 도메인 명세서](../../docs/Strategy%20도메인%20명세서.md) 17장과 같다. **우리만 좁았다.**

**데이터는 2018년부터 8년치가 있다.** `period` 상한을 데이터 부족으로 정당화할 수 없다.

| timeframe | 8년치 봉 수 | 우리가 건 상한 |
|---|---|---|
| `1H` | 약 70,000 | 720 |
| `4H` | 약 17,000 | 540 |
| `1D` | 약 2,900 | 365 |

**`topN` 상한은 의미가 없다.** 명세 13장이 "`topN`은 최대 선택 종목 수이며 N개를 강제로 채우지 않는다"고 정한다. 후보가 5개인데 `topN: 100`이면 5개를 산다.

`period` 상한은 애초에 우리 결정 사항이 아니다. 그 값으로 백테스트를 돌리는 건 백엔드고, 상한을 정하는 요인(데이터 조회 범위, 워밍업, 계산 비용)이 전부 실행하는 쪽에 있다.

**우리가 명세보다 좁으면 백엔드가 받을 전략을 우리가 거부한다.** 그 거부는 조용하지 않다 — 검증 실패로 잡혀 재시도를 태우고 사용자는 이유를 모르는 오류를 받는다.

## 깊이만 남기는 이유

성격이 다르다. 나머지는 숫자 하나라 값이 크든 작든 파서가 감당한다. **트리 깊이는 재귀 파싱이라 병리적으로 깊은 입력이 오면 스택을 태운다.**

즉 이 값은 백엔드와 합의할 도메인 규칙이 아니라 우리 파서를 지키는 선이다. 2에서 5로 넓힌다. `tests/fixtures/user_inputs.md` 34건(평균 22자)에 깊이 3 이상을 요구하는 문장이 없어 넘을 일이 없다.

**상수 옆에 그 취지를 주석으로 남겨라.** 나중에 "왜 이것만 있나"는 질문이 반드시 나온다.

## 검증 규칙을 명세 범위로

| 필드 | 바꿀 검증 |
|---|---|
| `FactorOperand.period` | `> 0`만 |
| `Ranking.period` | `> 0`만 |
| `Selection.topN` | `> 0`만 |
| `FilterGroup.children` | 최소 1만 |

`Strategy.validate_limits`에서 `max_periods` 딕셔너리, `periods` 수집, `conditions` 카운트가 모두 빠진다. 트리를 도는 목적이 깊이 검사 하나로 줄어든다.

## `MAX_USER_INPUT_LENGTH`는 건드리지 않는다

500자는 잠정값이 아니라 우리 방어선이다. 토큰 폭주를 막고 실측(발화 34건, 최대 37자)에 근거가 있다.

문서 쪽이 2000자로 어긋나 있던 것은 이 인계 명세와 같은 MR에서 500으로 맞췄다. `PLAN.md`, `TASKS.md`, `DECISIONS.md`.

## 테스트 정리

제거한 상한의 경계값·거부 케이스를 지운다. 깊이 케이스는 3 거부에서 **6 거부, 5 통과**로 바꾼다.

`period`와 `topN`의 하한 케이스(0, 음수 거부)는 남긴다. 명세가 `> 0`을 정하므로 계속 유효하다.

## 완료 조건

- 상수가 `MAX_FILTER_DEPTH` 하나만 남고 취지가 주석으로 적혀 있다
- `period`, `topN`이 `> 0`만 검증한다
- 깊이 6 트리가 거부되고 깊이 5는 통과한다
- 제거한 상한의 테스트 케이스가 정리된다
- `python -m pytest`가 GMS 키 없는 환경에서 통과한다

---

# H-1 의존성 주입 (3h)

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

# H-2 변환 엔드포인트 (5h)

## 만들 것

`app/api/v1/strategy.py`에 `POST /api/v1/ai/strategies/translate`. `app/main.py`에 라우터 등록.

## 라우터는 얇다

라우터가 하는 일은 셋이다. 요청 모델을 받고, 주입받은 translator를 부르고, `TranslateResponse`로 감싼다.

**분기를 라우터에 두지 마라.** 신규 생성과 수정을 가르는 것은 `currentStrategy`의 유무인데, 그 판단은 이미 `translate()` 안에 있다. 라우터에서 한 번 더 보면 정책이 두 군데로 흩어진다.

**재시도 소진 판정도 라우터에 두지 마라.** `translate()`가 `DSLValidationError`를 던지고 C-2 핸들러가 422로 바꾼다. 경로가 이미 이어져 있다.

`app/main.py`는 라우터 등록까지다. 비즈니스 로직이 들어가면 반려한다.

## 상태 코드는 이미 정해져 있다

| 상황 | 상태 | 코드 |
|---|---|---|
| 성공 | 200 | — |
| 요청 본문 검증 실패 | 400 | `INVALID_REQUEST` |
| 재시도 소진 | 422 | `DSL_VALIDATION_FAILED` |
| LLM 타임아웃 | 504 | `LLM_TIMEOUT` |
| LLM 업스트림 오류 | 502 | `LLM_UPSTREAM_ERROR` |
| 총량 초과 | 429 | S-2에서 새로 정한다 |

앞의 다섯은 C-2·C-3 핸들러가 이미 처리한다. **엔드포인트에서 예외를 잡아 응답으로 바꾸지 마라.** 그러면 변환이 두 군데가 된다.

## `changes`가 신규 생성을 표시하지 않는다

`describe_changes(None, ...)`가 빈 목록을 돌려준다. [TASKS.md](TASKS.md) G-6 케이스 1은 "`changes`에 신규 생성 표시"를 요구하는데 `tests/test_translator.py`가 `== []`로 못 박아 놨다. **둘 중 하나는 틀렸고, 응답 계약을 정하는 지금이 정할 때다.**

프론트 입장에서 "새로 만들었다"와 "바꿀 게 없었다"가 같은 빈 목록으로 오면 구분이 안 된다. 어느 쪽으로 정하든 코드·테스트·TASKS를 같이 맞춘다.

## 완료 조건

- `POST /api/v1/ai/strategies/translate`가 200을 준다
- 라우터에 신규·수정 분기와 소진 판정이 없다
- `app/main.py`에 비즈니스 로직이 없다
- 성공 응답이 `strategy` + `meta`(`attempts`·`model`·`elapsedMs`·`warnings`·`assumptions`·`changes`) 형태다
- 도메인 예외 4종이 각각 정해진 상태 코드로 나간다
- `uvicorn app.main:app` 기동 후 `/health/ready` 200

---

# S-2 남용 방어 — 짧은 경로와 총량 차단 (4h)

## 지키려는 것

**GMS 크레딧이다. 팀 공유고 유한하다.** 호출당 약 1,270토큰이니 장난 100회면 127,000토큰이 날아간다. 시연 중에 그러면 그날 크레딧이 끝난다.

요청 하나가 LLM 호출 최대 3회다. 막는 곳이 없으면 요청 수만큼 그대로 빠진다.

## 무상태 서버가 할 수 있는 것만 한다

**사용자당 호출 제한은 우리 몫이 아니다.** 이 서버는 무상태라 사용자별 카운터를 들 수 없다. 사용자를 아는 것은 Spring이고, 그쪽 제한은 L-4에서 합의한다.

우리가 할 수 있는 둘을 넣는다.

**짧은 경로** — LLM을 태우기 전에 걸러낼 수 있는 요청은 걸러낸다. 요청 본문 검증에서 이미 죽는 입력(빈 문자열, 500자 초과)이 LLM 호출까지 가면 안 된다. 이건 `TranslateRequest` 모델이 하는 일이라 이미 서 있고, **검증 실패가 400으로 끝나고 호출로 이어지지 않는지 테스트로 못 박는 것**이 실질이다.

**총량 차단** — 프로세스가 일정 시간 안에 태울 수 있는 LLM 호출 총량을 정하고 넘으면 429를 준다. 사용자별이 아니라 **서버 전체 총량**이라 무상태로도 셀 수 있다. 값과 창(窓)은 크레딧 잔량과 시연 부하를 보고 정하되, 근거를 `DECISIONS.md`에 적는다.

## 429 코드를 새로 정한다

지금 `app/core/errors.py`의 예외는 4종이고 429가 없다. 하나 늘린다. `code` 문자열은 Spring이 분기하는 값이라 정하면 안 바꾼다.

C-1이 정한 형태를 따른다 — 상태 코드와 코드 문자열을 속성으로 갖고, 핸들러가 `code`/`message`/`detail`로 바꾼다. **엔드포인트에서 직접 429를 만들지 마라.**

## 요청이 비용을 정하게 만들지 마라

`TranslateRequest`에 재시도 횟수·모델·타임아웃 필드를 넣지 않는다. 지금 없고 앞으로도 없어야 한다. 있으면 호출자가 우리 크레딧 소모량을 정한다.

`settings.max_retries`와 `settings.llm_timeout_seconds`가 유일한 통제점이다.

## 완료 조건

- 검증에서 죽는 입력이 LLM 호출로 이어지지 않는다 — 테스트가 호출 횟수를 센다
- 총량을 넘으면 429가 나가고 `code`·`message`·`detail` 형태를 지킨다
- 총량 값과 창의 근거가 `DECISIONS.md`에 있다
- 요청 본문이 재시도 횟수·모델·타임아웃을 지정할 수 없다
- 총량 차단이 헬스 엔드포인트를 막지 않는다

---

# H-3 API 테스트 (5h)

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
6. 엔드포인트가 예외를 잡아 응답으로 바꿈 — 변환은 핸들러 한 곳이다
7. 총량 차단 값이나 남긴 상한의 근거를 `DECISIONS.md`에 안 적음
8. 완료 보고에 커밋 SHA가 없음

# 이 구간에서 하지 않는 것

| 안 하는 것 | 언제 |
|---|---|
| 프롬프트 도메인 규칙 | K-3 (`-116`) |
| 골든 케이스 정답지 | K-4 (`-116`) |
| Spring-to-AI 인증 | 백엔드 계약 후 (L-4) |
| 사용자당 호출 제한 | Spring 몫. 무상태 서버가 사용자별 카운터를 들 수 없다 (L-4에서 합의) |
| `meta.warnings` 경고 규칙 | 티켓 없음. 아래 참조 |
| 계약 산출물 내보내기 | L-3 |

# 열려 있는 질문

| 질문 | 누구에게 | 막는 것 |
|---|---|---|
| 오류 `detail`의 키 표기 | 백엔드 | H-1 |
| 신규 생성 시 `changes`를 어떻게 표시할지 | 프론트 | H-2 |
| 총량 차단의 값과 창 | 팀 (크레딧 잔량) | S-2 |

**티켓 없이 떠 있는 것 하나.** `TranslateMeta.warnings`가 `app/services/translator.py:45`에서 `[]`로 하드코딩돼 있고 경고를 만드는 함수 자체가 없다. [DECISIONS.md](DECISIONS.md)의 "검증을 세 층으로 나눈다"가 경고 층을 정해 놨는데 통째로 비어 있다. **이 구간에서 하지 않는다** — S-1이 상한을 걷어내는 작업이라 경고 규칙을 같이 넣으면 두 성격이 한 커밋에 섞인다. 별도 티켓으로 뺀다.
