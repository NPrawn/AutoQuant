# 인계 명세 — AI 서버 개발 환경 (Epic A + C)

담당 스토리 [S15P21A107-3](https://ssafy.atlassian.net/browse/S15P21A107-3) 하위 8건. 합계 28h.

이 문서 하나로 8개 티켓을 끝낼 수 있게 쓴다. 배경이 필요하면 [PLAN.md](PLAN.md), 도메인 결정의 근거는 [DECISIONS.md](DECISIONS.md), 전체 작업 분해는 [TASKS.md](TASKS.md)를 본다.

## 이 구간의 성격

**전략 도메인 명세(API 스펙·ERD)가 아직 없다.** 그래서 지금은 명세가 와도 다시 쓰지 않을 것만 만든다. 프로젝트 골격, 설정 객체, 앱 부트, 예외 계층, 로깅까지다.

DSL 스키마(`app/core/schema.py`), 프롬프트 본문, LLM 호출은 **이 구간에 없다.** 만들지 않는다. 파일 자리만 비워 둔다.

## 작업 위치와 브랜치

작업 디렉토리는 저장소 `S15P21A107`의 `ai/`다. 지금은 플레이스홀더 README 하나뿐이다.

```bash
git checkout develop
git pull
git checkout -b feat/ai-server/A-1
```

- 브랜치는 `feat/ai-server/<작업ID>` 형식. `develop`에서 분기한다
- `main`/`master`/`develop`에 직접 푸시하지 않는다
- 완료 보고에 커밋 SHA를 포함한다. SHA 없는 보고는 반려한다

## 환경

**Python 버전은 고정하지 않는다.** 개발 장비 기준 3.14.6이고 의존성 전부 해당 버전 휠이 있다. `pyproject.toml`에 `requires-python = ">=3.11"` 하한만 둔다. 3.11은 문법상 하한일 뿐이다.

버전이 실제로 굳는 지점은 L-5 Dockerfile의 base image고, 그때 로컬에서 돌던 버전을 그대로 박는다. 지금 고정하면 아직 안 만든 컨테이너를 위해 개발 환경을 미리 묶는 셈이 된다.

`ai/README.md`에 "Python 3.12+"로 적혀 있는 건 플레이스홀더 시절 값이다. A-1에서 함께 고친다.

---

# A-1 프로젝트 초기화 ([S15P21A107-4](https://ssafy.atlassian.net/browse/S15P21A107-4), 4h)

## 디렉토리

```
ai/
├── requirements.txt
├── pyproject.toml
├── .gitignore
├── .env.example
├── README.md
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── api/
│   │   ├── __init__.py
│   │   └── v1/__init__.py
│   ├── core/__init__.py
│   ├── services/__init__.py
│   └── prompts/            (지금은 비어 있음)
└── tests/
    ├── __init__.py
    └── fixtures/__init__.py
```

## requirements.txt

```
fastapi
uvicorn[standard]
pydantic
pydantic-settings
openai>=1.60
pytest
httpx
```

각 항목이 왜 있는지, 그리고 왜 다른 것이 없는지는 아래 근거가 전부다. 여기 없는 패키지를 추가하려면 먼저 말한다.

| 패키지 | 이유 |
|---|---|
| `openai>=1.60` | LLM 호출은 SSAFY GMS 게이트웨이를 거친다. GMS는 키와 엔드포인트만 바꾸면 되는 프록시라 `base_url`만 교체하면 그대로 동작한다. 하한을 1.60으로 두는 건 `developer` 역할과 `response_format`의 `json_schema`를 쓰기 위해서다 |
| `httpx` | FastAPI `TestClient` 용도다. **LLM 호출에는 쓰지 않는다.** `openai` SDK가 내부에서 이미 쓰고 재시도·타임아웃·예외 타입까지 제공한다 |
| `pydantic-settings` | `python-dotenv` 대신이다. A-2의 설정 객체를 이걸로 만든다 |

**넣지 않는 것**

- `pytest-asyncio` — 지금 범위에 비동기 테스트가 없다. 필요해지면 그때 추가한다
- `python-dotenv` — `pydantic-settings`가 `.env` 로딩을 포함한다
- **LangChain** — 재시도 정책, 검증기의 스키마 주입, 프롬프트 조립, `changes` 생성을 모두 직접 통제하기로 이미 정했다([DECISIONS.md](DECISIONS.md) 결정 4). 넣으면 그 설계를 LangChain 방식으로 다시 하게 되고, 재시도 정책은 어차피 직접 짜게 된다

## pyproject.toml

pytest 설정만 담는다. 패키징 설정은 지금 필요 없다.

```toml
[tool.pytest.ini_options]
testpaths = ["tests"]
```

## .gitignore

`.venv/`, `__pycache__/`, `*.pyc`, `.env`, `.pytest_cache/`

**`.env`는 절대 커밋하지 않는다.** GMS 팀 키가 들어가는 파일이다.

## README.md 재작성

현재 내용이 실제 범위와 다르다. "시세·캔들 데이터로 매매 전략 추론", "Python 3.12+"로 적혀 있으나 실제는 **자연어를 전략 DSL(JSON)로 변환하는 서비스**이고 Python 3.11이다. [PLAN.md](PLAN.md) 기준으로 다시 쓴다.

담아야 할 것: 책임 한 줄, 호출 방향(Spring → AI 단방향), 담지 않는 것(DB·Redis 직접 접근, 인증, 시세 수집), 스택, 로컬 실행법, 환경변수 목록.

## 완료 조건

```bash
pip install -r requirements.txt
python -c "import app"
```

둘 다 성공. `ai/README.md`가 실제 책임과 스택을 설명한다.

---

# A-2 설정 객체 ([S15P21A107-5](https://ssafy.atlassian.net/browse/S15P21A107-5), 6h)

`app/core/config.py`, `.env.example`

## 환경변수

| 이름 | 기본값 | 예시 |
|---|---|---|
| `LLM_BASE_URL` | 없음 (필수) | `https://gms.ssafy.io/gmsapi/api.openai.com/v1` |
| `LLM_API_KEY` | 없음 (필수) | GMS 팀 키 |
| `LLM_MODEL` | 없음 (필수) | `gpt-5.4-mini` |
| `LLM_TIMEOUT_SECONDS` | 30 | |
| `MAX_RETRIES` | 2 | |
| `LOG_LEVEL` | INFO | |
| `APP_ENV` | 없음 (필수) | `local` |

## `LLM_PROVIDER`는 없다

이전 초안에 있었으나 뺐다. 호출 경로가 GMS 게이트웨이 하나라 분기할 값이 아니고, **공급자를 바꾼다는 것은 `LLM_BASE_URL`을 바꾼다는 뜻이다.** GMS가 upstream 호스트를 경로에 그대로 끼우는 방식이라 이렇게 간다.

| 공급자 | `LLM_BASE_URL` |
|---|---|
| OpenAI | `https://gms.ssafy.io/gmsapi/api.openai.com/v1` |
| Anthropic | `https://gms.ssafy.io/gmsapi/api.anthropic.com` |
| Gemini | `https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com` |

코드에 공급자 이름으로 분기하는 문자열이 생기면 반려한다.

## 규칙

- **`os.environ`을 읽는 파일은 이것 하나뿐이다.** 다른 파일에서 직접 읽으면 반려한다
- `.env.example`에는 키 이름만 적고 값은 비운다. `LLM_BASE_URL`만 위 예시 값을 적어 둔다
- 필수 환경변수가 없으면 **임포트 단계에서** 예외가 난다. 첫 요청에서 죽으면 안 된다
- 설정 객체를 `print()` 하거나 로그에 흘려도 API 키가 마스킹돼야 한다. `pydantic.SecretStr`을 쓰면 공짜로 얻는다

## 완료 조건

필수 변수를 하나 지우고 `python -c "import app.core.config"`를 돌리면 예외가 난다. 설정 객체 출력에 키 원문이 없다.

---

# A-3 앱 부트와 헬스 ([S15P21A107-6](https://ssafy.atlassian.net/browse/S15P21A107-6), 6h)

`app/main.py`, `app/api/v1/health.py`, `app/api/__init__.py`, `app/api/v1/__init__.py`

## 엔드포인트

| 경로 | 내용 |
|---|---|
| `GET /health` | 프로세스 생존 확인. `{"status": "ok"}` |
| `GET /health/ready` | 설정 로드와 프롬프트 파일 존재 확인. 실패 시 503 |

## 규칙

- **헬스체크에서 LLM API를 호출하지 않는다.** 과금 요인이 헬스체크에 붙으면 안 된다
- **CORS를 두지 않는다.** 프론트는 Spring만 호출하고, Spring이 이 서버를 부른다. 브라우저가 직접 올 일이 없다
- `main.py`에 비즈니스 로직을 한 줄도 두지 않는다. 앱 생성, 라우터 등록, 핸들러 등록까지다
- **지금은 `/health/ready`가 503인 게 정상이다.** `app/prompts/` 아래에 파일이 없는 시점이다. E-2 완료 후 200이 되는지 다시 확인한다. 503이 뜬다고 통과시키려고 검사를 무르게 만들면 안 된다

## 완료 조건

```bash
uvicorn app.main:app
curl localhost:8000/health
```

200 응답.

---

# C-1 예외 계층 ([S15P21A107-7](https://ssafy.atlassian.net/browse/S15P21A107-7), 2h)

`app/core/errors.py`

| 예외 | HTTP | 코드 |
|---|---|---|
| `LLMTimeoutError` | 504 | `LLM_TIMEOUT` |
| `LLMUpstreamError` | 502 | `LLM_UPSTREAM_ERROR` |
| `DSLValidationError` | 422 | `DSL_VALIDATION_FAILED` |
| `PromptTooLongError` | 400 | `INVALID_REQUEST` |

그 밖의 예외는 500 `INTERNAL_ERROR`.

## `NotAStrategyError`는 없다

이전 초안에 있었으나 뺐다. [DECISIONS.md](DECISIONS.md) 결정 5가 **"무조건 전략을 만든다"**로 확정됐다. "안녕하세요" 같은 입력이 와도 거부하지 않고 기본값을 채워 전략을 응답한다.

대신 조건이 하나 붙는다. 임의로 정한 값을 `TranslateMeta.assumptions`에 담아 알린다. 그 필드는 B-4 범위라 이 구간에서는 만들지 않지만, 예외를 뺀 이유가 여기 있다는 것은 알고 있어야 한다.

## 규칙

- 예외를 HTTP 응답으로 바꾸는 변환은 여기서 하지 않는다. C-2의 핸들러가 맡는다
- 오류 `message`는 **개발자용**이다. 사용자에게 보일 문구는 Spring이나 프론트가 다시 만든다. 무슨 일이 났는지 정확히 적는 데 집중한다
- `code`는 기계가 분기하는 값이므로 안정적으로 유지한다. 나중에 문자열을 바꾸면 Spring 쪽이 깨진다

---

# C-2 예외 핸들러 ([S15P21A107-8](https://ssafy.atlassian.net/browse/S15P21A107-8))

`app/main.py`에 등록한다. 도메인 예외를 던지면 이 형태로 나가야 한다.

```json
{"code": "LLM_TIMEOUT", "message": "...", "detail": null}
```

`detail`은 객체 또는 `null`. 스택 트레이스를 담지 않는다.

---

# C-3 요청 검증 오류 오버라이드 ([S15P21A107-9](https://ssafy.atlassian.net/browse/S15P21A107-9))

FastAPI는 잘못된 요청 본문에 `{"detail": [...]}` 형태로 422를 낸다. 우리 계약은 `code`/`message`/`detail`이고 상태는 **400**이다. `RequestValidationError` 핸들러로 덮는다.

오류 코드는 `INVALID_REQUEST`. 어느 필드가 왜 틀렸는지는 `detail`에 담는다.

---

# C-4 요청 ID와 구조화 로깅 ([S15P21A107-10](https://ssafy.atlassian.net/browse/S15P21A107-10))

`app/core/logging.py`, `app/main.py`

- 요청 헤더에 요청 ID가 있으면 **이어받고**, 없으면 생성한다. Spring이 붙여 보낼 값이라 새로 만들어 덮으면 추적이 끊긴다
- 응답 헤더로 되돌려준다
- 모든 로그 줄에 요청 ID가 들어간다
- **API 키가 로그에 나오면 안 된다.** 설정 객체를 통째로 로깅하는 코드가 제일 흔한 사고 경로다

---

# C-5 오류 응답 테스트 ([S15P21A107-11](https://ssafy.atlassian.net/browse/S15P21A107-11), 2h)

`tests/test_errors.py`

| 예외 | 기대 상태 | 기대 코드 |
|---|---|---|
| `LLMTimeoutError` | 504 | `LLM_TIMEOUT` |
| `LLMUpstreamError` | 502 | `LLM_UPSTREAM_ERROR` |
| `DSLValidationError` | 422 | `DSL_VALIDATION_FAILED` |
| `PromptTooLongError` | 400 | `INVALID_REQUEST` |
| 정의되지 않은 예외 | 500 | `INTERNAL_ERROR` |

추가 확인 세 가지 — 응답 본문에 스택 트레이스가 없다, API 키가 없다, 모든 응답이 `code`/`message`/`detail` 세 필드를 가진다.

**실제 LLM API를 치지 않는다.** 가짜 구현으로 예외만 던져서 확인한다. **GMS 키가 없는 환경에서도 이 테스트는 돌아야 한다.**

---

# Spring 연결과 보안 — 지금 정할 것 / 미룰 것

## 지금 정하는 것

**포트는 8000.** 팀 저장소에서 이미 쓰는 포트가 8080(api-market), 8081(api-trading)이라 겹치지 않는다. uvicorn 기본값이기도 해서 따로 설정할 게 없다. 백엔드가 `http://localhost:8000`으로 부르면 된다. `ai/README.md`에 명시한다.

**로컬 바인딩은 `127.0.0.1`.** `uvicorn app.main:app`의 기본값이 이미 그렇다. `--host 0.0.0.0`을 붙이지 않는다. 붙이는 순간 같은 네트워크의 아무나 GMS 크레딧을 쓸 수 있는 엔드포인트가 열린다. 컨테이너로 옮기는 L-5에서만 바꾼다.

**CORS를 두지 않는다.** 프론트는 Spring만 호출한다. 브라우저가 이 서버에 직접 올 일이 없으므로 CORS 미들웨어를 넣으면 열지 않아도 될 문을 여는 것이다.

**요청 ID 헤더 이름은 `X-Request-ID`.** C-4에서 이어받고 되돌려주는 그 헤더다. 환경변수로 빼지 않는다. 양쪽이 합의한 상수 하나면 된다.

## 환경변수를 새로 만들지 않는 이유

포트와 호스트는 `uvicorn` 실행 인자로 넘긴다. 환경변수로 한 겹 더 감싸면 읽는 곳이 늘고 A-2의 "`os.environ`은 `config.py`에서만"이 흐려진다. 컨테이너에서 포트를 바꿔야 하는 시점은 L-5고, 그때는 Dockerfile의 `CMD`가 그 자리를 맡는다.

## 인증은 미룬다

Spring↔AI 사이 인증은 **지금 넣지 않는다.** [DECISIONS.md](DECISIONS.md)에 이미 확정된 항목이다 — 백엔드 설계가 나오기 전이라 무엇에 맞출지 정할 근거가 없다.

다만 **미들웨어 자리는 비워 둔다.** C-4에서 요청 ID 미들웨어를 넣을 때 인증 미들웨어가 나중에 들어갈 위치를 주석 한 줄로 표시한다. 나중에 함수 하나 추가로 끝나야 한다.

지금 방어선은 두 개다. 서버가 `127.0.0.1`에만 붙어 있다는 것, 그리고 GMS 키가 `.env`에만 있고 로그·응답 어디에도 안 나온다는 것. 로컬 개발 단계에서는 이걸로 충분하다.

## L-4에서 Spring과 합의할 것 (지금은 목록만)

통합 티켓에서 다룬다. 여기서 미리 정해 봐야 상대가 없다.

| 항목 | 우리 쪽 현재 값 |
|---|---|
| 인증 방식 | 없음. 공유 시크릿 헤더가 유력 |
| 타임아웃 | Spring이 우리를 기다릴 시간. 우리 `LLM_TIMEOUT_SECONDS` 30에 재시도 2회가 얹히므로 그 합보다 길어야 한다 |
| 요청 ID 헤더 | `X-Request-ID` |
| 오류 형식 | `code` / `message` / `detail`. `code`는 기계 분기용이라 안정적으로 유지 |
| 기본 URL | `http://localhost:8000` |

**타임아웃이 제일 자주 사고 나는 항목이다.** Spring이 10초에 끊는데 우리가 최악의 경우 90초를 쓰면 정상 응답이 전부 타임아웃으로 보인다. L-2에서 실측한 응답 시간 중앙값과 상위 95%를 근거로 넘긴다.

# 도커는 지금 만들지 않는다

`Dockerfile`은 L-5 범위다. 지금 만들지 않는 이유 셋.

1. **담을 게 없다.** A-3까지 끝나도 헬스 엔드포인트뿐이다
2. **Python 버전을 지금 고르게 된다.** base image를 정하는 순간 버전이 굳는다. L-5까지 미루면 그때 실제로 돌던 버전을 그대로 박을 수 있다
3. **팀 compose는 인프라 전용이다.** `infra/docker-compose.yml`에 Postgres·Redis·Kafka만 있고 backend·frontend 앱은 들어 있지 않다. AI 서버만 예외로 넣을 이유가 없다

로컬 실행은 `uvicorn app.main:app --reload`다. 이게 A-3의 완료 조건이기도 하다.

**L-5에서 이 서버는 자기 이미지를 갖는다.** 같은 저장소에 있을 뿐 `backend/api`(JVM), `ingest`(Go)와 런타임이 달라 한 이미지에 담을 이유가 없다. 그때 바뀌는 것 둘을 미리 적어 둔다.

- 바인딩이 `127.0.0.1`에서 `0.0.0.0`으로 바뀐다. 로컬에서 `127.0.0.1`이 하던 방어를 컨테이너 네트워크 격리가 대신한다
- Spring이 부르는 주소가 `localhost:8000`에서 compose 서비스명으로 바뀐다. 백엔드가 이 값을 하드코딩하지 않게 L-4에서 짚는다

지금 코드에 영향은 없다. A-3에서 uvicorn 인자를 코드에 박지만 않으면 된다.

# 반려 사유 정리

아래에 걸리면 다시 보낸다.

1. `app/core/config.py` 밖에서 `os.environ`을 읽음
2. 공급자 이름으로 분기하는 문자열이 코드에 생김
3. `requirements.txt`에 위 목록 밖의 패키지가 협의 없이 추가됨
4. `.env`가 커밋됨
5. 로그나 오류 응답에 API 키가 노출됨
6. `main.py`에 비즈니스 로직이 들어감
7. 완료 보고에 커밋 SHA가 없음
8. 완료 조건에 적힌 명령이 실제로 통과하지 않음

# 이 구간에서 하지 않는 것

- `app/core/schema.py` (DSL 정의) — 전략팀 명세 대기
- `app/services/llm_client.py` (LLM 호출) — Epic F
- `app/prompts/` 아래 프롬프트 본문 — Epic E
- 미니 백테스터 — [DECISIONS.md](DECISIONS.md) 결정 6, 명세·ERD 나온 뒤 재검토

# 열려 있는 질문

- **GMS 팀 키 발급 여부.** A-2까지는 없어도 되지만(`.env.example`에 이름만 적으므로) Epic F 착수 전에는 필요하다
