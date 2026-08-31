# ai — AI 서버

자연어로 설명한 투자 전략을 검증된 전략 DSL(JSON)로 바꿔 돌려주는 FastAPI 서버. Spring 백엔드가 HTTP로 호출한다.

**이 파일은 `ai/` 아래에서 작업할 때의 규칙이다.** 저장소 다른 파트(`backend/`, `frontend/`)에는 적용되지 않는다.

## 작업 위치

에이전트는 `ai/`에서 띄운다.

```bash
cd S15P21A107/ai && claude
```

저장소 루트에서 띄워도 `ai/` 파일을 건드리는 순간 이 파일이 로드되지만, `ai/`에서 띄우면 조건 없이 최상위 규칙이 되고 다른 파트(Java·Go·React)가 컨텍스트에 섞이지 않는다.

git은 하위 디렉토리에서도 `.git`을 위로 찾으므로 브랜치·커밋 모두 여기서 그대로 된다.

## 문서

작업 전에 [docs/README.md](docs/README.md)를 본다. 문서 색인과 갱신 규칙이 거기 있다.

| 파일 | 언제 |
|---|---|
| [docs/HANDOFF-A.md](docs/HANDOFF-A.md) | 지금 티켓을 잡았을 때 |
| [docs/DECISIONS.md](docs/DECISIONS.md) | "왜 A가 아니라 B인가" |
| [docs/TASKS.md](docs/TASKS.md) | 무엇을 어느 순서로 |
| [docs/PLAN.md](docs/PLAN.md) | 전체 구조가 왜 이런지 |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | 같은 데서 또 막혔을 때 |

## 지켜야 하는 것

### DSL 필드명은 `app/core/schema.py` 한 곳에만 적는다

전략 도메인 명세가 아직 오지 않아 현재 스키마는 **임시안이고 통째로 교체된다.** 필드명이 라우터·프롬프트·테스트에 문자열로 흩어져 있으면 교체가 전면 수정이 된다.

다른 파일에서 필드명이나 허용값을 문자열로 다시 적으면 반려한다. 검증기도 스키마 타입을 **주입받는다** — `Strategy`를 직접 임포트해 하드코딩하지 않는다.

계약 문서(OpenAPI 명세, DSL JSON Schema, 예시)는 손으로 쓰지 않고 L-3에서 스크립트로 코드에서 뽑는다.

### 환경변수는 `app/core/config.py`에서만 읽는다

`os.environ`을 읽는 파일은 이것 하나다. 필수 변수가 없으면 **임포트 단계에서** 예외가 나야 한다. 첫 요청에서 죽으면 안 된다.

| 이름 | 기본값 |
|---|---|
| `LLM_BASE_URL` | 없음 (필수) |
| `LLM_API_KEY` | 없음 (필수) |
| `LLM_MODEL` | 없음 (필수) |
| `LLM_TIMEOUT_SECONDS` | 30 |
| `MAX_RETRIES` | 2 |
| `LOG_LEVEL` | INFO |
| `APP_ENV` | 없음 (필수) |

### API 키가 새지 않게 한다

`.env`를 커밋하지 않는다. 설정 객체를 `print()` 하거나 로그에 흘려도 키가 마스킹돼야 한다. 오류 응답 본문에 스택 트레이스와 키가 없어야 한다.

설정 객체를 통째로 로깅하는 코드가 제일 흔한 사고 경로다.

### LLM 호출은 GMS 게이트웨이 하나를 거친다

여러 공급자를 하나의 API로 묶어 주는 프록시라 `openai` SDK의 `base_url`만 GMS로 바꿔 쓴다. 공급자 접점은 `app/services/llm_client.py` 한 파일이다.

- 모델 교체는 `LLM_MODEL` 한 줄, 공급자 교체는 `LLM_BASE_URL`까지 두 줄이어야 한다
- 함수 시그니처에 특정 공급자의 타입이 새어 나가면 반려한다
- 코드에 공급자 이름으로 분기하는 문자열이 생기면 반려한다
- **LangChain을 쓰지 않는다.** 재시도 정책, 검증기 스키마 주입, 프롬프트 조립, `changes` 생성을 모두 직접 통제하기로 정했다

근거는 [docs/DECISIONS.md](docs/DECISIONS.md) 결정 4.

### 재시도할 때 대화를 누적하지 않는다

매 시도는 시스템 프롬프트, 원래 입력, 직전 출력, 검증 오류로 구성된 **새 요청**이다. 타임아웃과 공급자 오류는 재시도하지 않고 그대로 올린다.

`changes`는 LLM에게 만들게 하지 않는다. 두 `Strategy` 객체를 비교해 서버가 생성한다. LLM이 바꿨다고 말한 것과 실제로 바뀐 것이 다를 수 있다.

### 오류 응답 형식은 Spring과의 계약이다

모든 오류가 `code` / `message` / `detail` 세 필드를 가진다. `code`는 기계가 분기하는 값이라 안정적으로 유지한다. `message`는 개발자용이다 — 사용자에게 보일 문구는 Spring이나 프론트가 다시 만든다.

FastAPI 기본 `{"detail": [...]}` 형식이 밖으로 새면 안 된다.

### 안 하는 것

- **CORS를 두지 않는다.** 프론트는 Spring만 호출하고 Spring이 이 서버를 부른다. 브라우저가 직접 올 일이 없다
- **`--host 0.0.0.0`을 붙이지 않는다.** 붙이는 순간 같은 네트워크의 누구나 GMS 크레딧을 쓸 수 있는 엔드포인트가 열린다. 컨테이너로 옮기는 L-5에서만 바꾼다
- **헬스체크에서 LLM API를 호출하지 않는다.** 과금 요인이 헬스체크에 붙으면 안 된다
- **`main.py`에 비즈니스 로직을 두지 않는다.** 앱 생성, 라우터 등록, 핸들러 등록까지다
- **DB·Redis에 직접 붙지 않는다.** 데이터가 필요하면 요청 페이로드로 받는다. 이 서버는 무상태다

## 스택

Python 3.11 이상 (`pyproject.toml`에 하한만. 개발 장비는 3.14.6). FastAPI, Pydantic v2, `openai` SDK, pytest.

의존성은 아래가 전부다. 여기 없는 패키지를 추가하려면 먼저 말한다.

```
fastapi
uvicorn[standard]
pydantic
pydantic-settings
openai>=1.60
pytest
httpx
```

`httpx`는 FastAPI `TestClient` 용도다. LLM 호출에는 쓰지 않는다.

## 실행

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload
```

포트 8000. 팀이 8080(api-market), 8081(api-trading)을 쓰고 있어 겹치지 않는다.

`GET /health`는 프로세스 생존, `GET /health/ready`는 설정과 프롬프트 파일 존재를 본다. **프롬프트 파일이 없는 지금은 `/health/ready`가 503인 게 정상이다.** 503이 뜬다고 통과시키려고 검사를 무르게 만들지 않는다.

## 팀 컨벤션

백엔드 컨벤션 문서를 따른다. Python에 그대로 오지 않는 항목은 대응되는 쪽으로 읽는다.

**코드 스타일**

- 모든 파일의 마지막은 개행으로 끝낸다
- 와일드카드 임포트(`from x import *`)를 쓰지 않는다

**API 경로**

- 리소스명은 **복수형**이다. `POST /api/v1/ai/strategies/translate`
- 여러 단어는 kebab-case
- 파라미터는 camelCase

**이름**

요청 DTO는 `XXXRequest`, 응답 DTO는 `XXXResponse`. 우리 `TranslateRequest`·`TranslateResponse`가 여기 해당한다.

**디렉터리 대응**

| 컨벤션 | 우리 |
|---|---|
| `global/config` | `app/core/config.py` |
| `global/exception` | `app/core/errors.py` |
| `global/response` | `app/api/error_handlers.py` |
| 도메인별 패키지 | `app/api/v1/`, `app/services/` |

## 브랜치와 커밋

- `develop`에서 **`feat/{이슈번호}`** 형식으로 분기한다 (예: `feat/3`). 마디를 더 늘리지 않는다. 스토리 티켓을 만들면 Jira 자동화가 이 브랜치를 함께 만든다 — 손으로 만들지 말고 받아 쓴다
- **하위 작업마다 브랜치를 파지 않는다.** 스토리 하나에 브랜치 하나, 하위 작업은 커밋 하나씩이다
- `feat` → `develop`은 merge, `develop` → `master`는 merge commit으로 병합한다
- **`develop`과 `master`에는 force-push와 삭제를 하지 않는다.** 작업 브랜치에만 허용된다
- 커밋 메시지는 Conventional Commits(`feat:` `fix:` `docs:` `refactor:` `chore:`)를 쓰고, footer에 `Refs: S15P21A107-N`을 붙인다
- 완료 보고에 커밋 SHA를 포함한다. SHA 없는 보고는 반려한다
- 각 티켓의 완료 조건에 적힌 명령이 실제로 통과해야 완료다

## Jira

프로젝트 `S15P21A107`, 에픽 [S15P21A107-12](https://ssafy.atlassian.net/browse/S15P21A107-12).

하위 작업 요약은 `docs/TASKS.md`의 작업 ID로 시작한다 (예: `A-1 프로젝트 초기화`). 보드와 문서가 갈라지지 않게 하려는 것이다.

**에픽 `S15P21A107-12` 밖의 티켓은 읽기만 한다.** 수정·삭제·상태 변경·담당자 변경 금지. 담당자가 비어 있어도 마찬가지다.

## 반려 사유 정리

1. `app/core/config.py` 밖에서 `os.environ`을 읽음
2. DSL 필드명·허용값을 `app/core/schema.py` 밖에서 문자열로 다시 적음
3. 공급자 이름으로 분기하는 문자열이 코드에 생김
4. 의존성 목록 밖의 패키지가 협의 없이 추가됨
5. `.env`가 커밋됨
6. 로그나 오류 응답에 API 키가 노출됨
7. `main.py`에 비즈니스 로직이 들어감
8. 완료 보고에 커밋 SHA가 없음
9. 완료 조건에 적힌 명령이 실제로 통과하지 않음
