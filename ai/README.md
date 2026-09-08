# AI 전략 변환 서비스

자연어로 설명한 투자 전략을 검증 가능한 전략 DSL(JSON)로 바꾸는 FastAPI 서비스다.

## 서비스 경계

Spring 백엔드가 HTTP로 이 서비스를 호출한다. 호출 방향은 한쪽뿐이다.

```text
Spring 백엔드 -> AI 서비스
```

이 서비스는 DB나 Redis에 직접 연결하지 않고, 사용자를 인증하거나 시세를 수집하지도 않는다. 필요한 데이터는 Spring이 요청에 담아 전달한다. 상태를 갖지 않는다.

## 구성

- Python 3.11 이상 (개발 장비 3.14.6)
- FastAPI, Uvicorn
- Pydantic v2, pydantic-settings
- SSAFY GMS 게이트웨이를 거치는 OpenAI SDK
- pytest, httpx

## 로컬 실행

```bash
python -m pip install -r requirements.txt
cp .env.example .env      # 필수 항목을 채운다
uvicorn app.main:app --reload
```

8000 포트, loopback 바인딩이다. **로컬 실행에 `--host 0.0.0.0`을 붙이지 않는다.** 붙이는 순간 같은 네트워크의 누구나 GMS 크레딧을 쓸 수 있는 엔드포인트가 열린다.

## 컨테이너 실행

```bash
docker build -t pintect-ai .
docker run --rm -p 8000:8000 --env-file .env pintect-ai
```

이미지 안에서는 `--host 0.0.0.0`으로 뜬다. 컨테이너 인터페이스에 바인딩하지 않으면 Spring이 부르지 못한다. **그것과 호스트에 포트를 여는 것은 별개다** — 위의 `-p`는 로컬에서 직접 쳐 볼 때만 쓴다.

환경변수는 런타임에 주입한다. `.env`는 `.dockerignore`에 있어 이미지에 들어가지 않고, `ENV`로 굽는 값도 없다. **키가 레이어에 박히면 이미지를 받는 누구나 읽는다.**

필수 환경변수가 없으면 컨테이너가 임포트 단계에서 죽는다. 첫 요청까지 가지 않는다.

헬스체크는 `/health`를 친다. LLM을 부르지 않는다 — 과금 요인이 헬스체크에 붙으면 컨테이너가 살아 있는 동안 계속 크레딧을 쓴다.

## 환경변수

`.env.example`을 `.env`로 복사해서 쓴다. **`.env`는 커밋하지 않는다.**

**필수 넷** — 없으면 임포트 단계에서 예외가 난다. 첫 요청까지 가지 않는다.

| 이름 | 예시 |
|---|---|
| `LLM_BASE_URL` | `https://gms.ssafy.io/gmsapi/api.openai.com/v1` |
| `LLM_API_KEY` | GMS 팀 키 |
| `LLM_MODEL` | `gpt-5.4-mini` |
| `APP_ENV` | `local` |

**선택 셋** — `LLM_TIMEOUT_SECONDS`, `MAX_RETRIES`, `LOG_LEVEL`이다.

**기본값은 여기 적지 않는다.** [`app/core/config.py`](app/core/config.py)의 필드 기본값이 정본이고, README가 숫자를 베끼면 원본이 바뀔 때 조용히 낡는다.

선택 항목을 `.env`에서 **빈 값으로 두면 안 된다.** 주석 처리하거나 값을 넣는다. `LLM_TIMEOUT_SECONDS=`처럼 비워 두면 빈 문자열이 정수 파싱에 걸려 기동이 실패하고, `LOG_LEVEL=`은 예외 없이 기본값 `INFO`를 빈 문자열로 덮어쓴다.

환경변수를 읽는 파일은 `app/core/config.py` 하나다. 다른 파일에서 `os.environ`을 직접 읽지 않는다.

**공급자를 바꾼다는 것은 `LLM_BASE_URL`을 바꾼다는 뜻이다.** GMS가 upstream 호스트를 경로에 그대로 끼우는 구조라 Anthropic은 `.../gmsapi/api.anthropic.com`, Gemini는 `.../gmsapi/generativelanguage.googleapis.com`으로 간다. 코드에 공급자 이름으로 분기하는 문자열을 두지 않는다.

## 엔드포인트

| 경로 | 응답 |
|---|---|
| `GET /health` | 200 `{"status": "ok"}` |
| `GET /health/ready` | 프롬프트 파일이 있으면 200, 없으면 503 |

`/health/ready`는 프롬프트 파일이 들어와 200이다. 503이 뜨면 프롬프트 로딩이 깨진 것이니, 통과시키려고 검사를 무르게 만들지 않는다.

헬스체크는 LLM API를 호출하지 않는다. 과금 요인이 헬스체크에 붙으면 안 된다.

## 오류 응답

**모든 오류가 세 필드를 갖는다.** Spring과의 계약이다.

```json
{
  "code": "LLM_TIMEOUT",
  "message": "...",
  "detail": null
}
```

`detail`은 객체 또는 `null`이다. `code`는 기계가 분기하는 값이라 안정적으로 유지한다. `message`는 개발자용이고, 사용자에게 보일 문구는 Spring이나 프론트가 다시 만든다.

| 상황 | 상태 | `code` |
|---|---|---|
| LLM 응답 시간 초과 | 504 | `LLM_TIMEOUT` |
| LLM 게이트웨이 오류 | 502 | `LLM_UPSTREAM_ERROR` |
| 서킷이 열려 호출하지 않음 | 503 | `LLM_CIRCUIT_OPEN` |
| 분당 총량 초과 | 429 | `LLM_CREDIT_LIMIT_EXCEEDED` |
| 생성된 DSL이 스펙 위반 | 422 | `DSL_VALIDATION_FAILED` |
| 요청 본문이 잘못됨 | 400 | `INVALID_REQUEST` |
| 그 밖 | 500 | `INTERNAL_ERROR` |

정의처는 [`app/core/errors.py`](app/core/errors.py) 하나다.

요청 검증 실패의 `detail`에는 필드 경로와 사유만 담는다. 보낸 값은 되돌려주지 않는다.

500 응답의 `message`는 고정 문구다. 실제 예외와 스택 트레이스는 로그에만 남는다.

## 테스트

```bash
python -m pytest
```

**GMS 키 없이 전부 돈다.** 기본 실행에는 실제 LLM API를 치는 테스트가 없다. 필요한 환경변수는 테스트가 직접 주입한다.

### `-m live`는 크레딧을 쓴다

측정용 세트는 `live` 마커로 분리돼 기본 실행에서 빠진다. 돌리면 실제 호출이 나가고 팀 크레딧이 줄어든다.

```bash
python -m pytest -m live -k golden     # 신규 생성 34건
python -m pytest -m live -k holdout    # 홀드아웃 28건
python -m pytest -m live -k edit       # 편집 12건
```

| 세트 | 회차당 프롬프트 토큰 |
|---|---|
| 골든 34건 | 약 42,000 |
| 홀드아웃 28건 | 약 50,000 |
| 편집 12건 | 약 25,000 |

**홀드아웃은 프롬프트를 크게 고친 뒤에만 돌린다.** 평소 회귀 확인은 골든과 편집으로 한다. 그리고 **홀드아웃 실패를 보고 프롬프트를 고치면 그 세트는 홀드아웃이 아니게 된다** — 규칙은 [`tests/fixtures/holdout_inputs.md`](tests/fixtures/holdout_inputs.md) 머리에 있다.

### 무엇을 안 덮는가

덮는 것은 스키마 검증, 응답 정제와 재시도, 오류 계약, 프롬프트 조립, 서킷 브레이커와 총량 상한, 경고 규칙, 계약 산출물의 재생성 일치다. 공급자 호출은 가짜 클라이언트로 대신한다.

안 덮는 것은 이렇다.

- **LLM 출력의 타당성.** 형식과 스케일은 기계로 보지만 "이 매핑이 맞는가"는 사람이 표본으로 본다. `live` 세트가 재는 것도 정확도가 아니라 정답지와의 일치도다
- **동시성.** 서킷과 총량 상한은 단일 스레드 테스트로만 확인했다. 반열림 창에 동시 요청이 몰리면 시험 호출이 한 건이 아니고, 워커를 늘리면 상한이 워커 수만큼 곱해진다
- **컨테이너 기동.** `docker build`와 헬스 응답은 손으로 확인했고 테스트가 아니다
- **Spring과의 실제 연동.** 양쪽을 함께 띄운 통합 테스트가 없다. Spring 쪽 계약은 Java 테스트가 따로 본다

**숫자로 된 커버리지 목표는 두지 않는다.** 위 목록이 정보이고 비율은 아니다.

## Spring 연동 계약

Spring이 이 서비스를 부르는 방식이다. **값은 여기서 다시 정하지 않고 정의처를 가리킨다.** README가 숫자를 베끼면 사본이 하나 더 생기고, 사본은 원본이 바뀔 때 조용히 낡는다.

### 오류 코드는 Spring이 자기 형식으로 바꾼다

우리 `code`는 밖으로 나가지 않는다. Spring이 `ApiErrorCode`로 옮겨 `timestamp`·`status`·`code`·`message`·`path`·`validationErrors` 형식으로 내보낸다. **한 서버가 두 오류 형식으로 말하지 않기 위해서다** — 프론트가 경로에 따라 두 체계를 알아야 하면 분기가 두 배가 된다.

| 상황 | AI 서비스 | Spring |
|---|---|---|
| 재시도 소진 | 422 `DSL_VALIDATION_FAILED` | `AI-422-001` |
| 분당 총량 초과 | 429 `LLM_CREDIT_LIMIT_EXCEEDED` | `AI-429-001` |
| 게이트웨이 오류 | 502 `LLM_UPSTREAM_ERROR` | `AI-503-001` |
| 서킷 열림 | 503 `LLM_CIRCUIT_OPEN` | `AI-503-001` |
| 응답 시간 초과 | 504 `LLM_TIMEOUT` | `AI-504-001` |
| 요청 본문 오류 | 400 `INVALID_REQUEST` | `COMMON-400-001` |

**502와 503이 한 코드로 묶인다.** 프론트가 보기에 게이트웨이가 죽은 것과 AI 서비스가 안 뜬 것은 같은 상황이라 그렇게 정했다. `AI-502-001`은 다른 뜻이다 — 우리 응답을 Spring이 파싱하지 못했을 때 쓴다.

매핑은 [`AiStrategyTranslateWebClient.toErrorCode()`](../backend/api/src/main/java/com/ssafy/a107/ai/infrastructure/AiStrategyTranslateWebClient.java), 코드 정의는 [`ApiErrorCode`](../backend/api/src/main/java/com/ssafy/a107/common/exception/ApiErrorCode.java)에 있다.

우리 `detail`(`attempts`·`lastErrors`)은 Spring 응답으로 나가지 않는다. `ApiErrorResponse`에 담을 자리가 없고, `lastErrors`는 LLM 출력에서 나온 문자열이라 밖으로 내보내려면 매번 봐야 한다. Spring이 로그에만 남긴다.

### 요청 식별자

헤더는 `X-Request-ID`. **규칙은 세 홉이 같다 — 있으면 이어받고 없으면 만든다.** 각 홉이 같은 규칙을 쓰면 어느 지점에서 추적을 시작하든 그 아래가 이어진다. 프론트가 아직 안 보내므로 지금은 Spring이 만든다.

Spring은 `[A-Za-z0-9._-]{1,64}`에 맞는 값만 이어받고 어긋나면 새로 만든다. 클라이언트가 보낸 문자열이 두 서버의 로그 식별자 자리에 그대로 앉기 때문이다.

받는 쪽은 [`app/main.py`](app/main.py)의 `add_request_context`, 보내는 쪽은 [`RequestIdFilter`](../backend/api/src/main/java/com/ssafy/a107/common/logging/RequestIdFilter.java)와 `AiStrategyTranslateWebClient`다.

### 타임아웃이 두 개다

| 무엇을 기다리나 | 값 | 정의처 |
|---|---|---|
| 우리가 GMS를 | `LLM_TIMEOUT_SECONDS` 기본 30초 | [`app/core/config.py`](app/core/config.py) |
| Spring이 우리를 | `ai.timeout` 10초 | [`application.yml`](../backend/api/src/main/resources/application.yml) |

**Spring이 우리를 기다리는 시간이 우리가 GMS를 기다리는 시간보다 짧다.** 실측 중앙값 2,270~2,619ms, 상위 95% 4,353~4,649ms라 정상 응답은 10초 안에 들어온다. GMS가 30초를 끌면 Spring이 먼저 끊고 `AI-504-001`을 내는 것이 맞다 — 사용자를 30초 기다리게 하지 않는다.

### 호출 한도가 두 겹이다

| 어디 | 한도 | 무엇을 지키나 |
|---|---|---|
| AI 서비스 | 프로세스 총량 분당 30회 (`MAX_LLM_CALLS_PER_MINUTE`, [`translator.py`](app/services/translator.py)) | GMS 크레딧 |
| Spring | 회원당 분당 10회 (`ai.translations-per-minute-per-member`) | 한 사용자의 폭주 |

**사용자 식별은 Spring만 할 수 있다.** 우리는 무상태고 요청 본문에 회원 정보가 없다. 반대로 크레딧은 우리 프로세스 전체를 봐야 하므로 우리 쪽이다. 한쪽이 다른 쪽을 대신하지 못한다.

### 서킷 브레이커가 있다

연속 실패가 상한에 닿으면 일정 시간 동안 LLM을 부르지 않고 즉시 503 `LLM_CIRCUIT_OPEN`을 낸다. 값과 동작은 [`app/services/circuit_breaker.py`](app/services/circuit_breaker.py)에 있다.

**일시적 상태다.** 차단 시간이 지나면 시험 호출을 흘려보내고 성공하면 닫힌다. 받는 쪽이 이것을 영구 장애로 다루면 재시도 정책을 잘못 세운다.

게이트웨이 장애(`LLM_TIMEOUT`·`LLM_UPSTREAM_ERROR`)만 실패로 센다. 검증 실패와 총량 초과는 우리 쪽 사정이라 세지 않는다. 근거는 [`docs/DECISIONS.md`](docs/DECISIONS.md) 결정 14.

### `strategy`가 `null`일 수 있다

전략 설명이 아닌 입력에는 LLM을 부르지 않고 `strategy: null`, `attempts: 0`, 사유가 담긴 `assumptions`를 돌려준다. **오류가 아니라 200이다.** Spring `TranslateStrategyResponse.strategy`가 nullable인 이유가 이것이다.

### 계약 산출물

기계가 뽑은 정본이 [`docs/contract/`](docs/contract/) 아래에 있다. OpenAPI 명세, DSL JSON Schema, 응답 예시 다섯이다. **손으로 고치지 않는다** — `python scripts/export_contract.py`로 다시 뽑고, 코드와 어긋나면 `pytest`가 실패한다.

## 역할 2를 붙일 자리

`PLAN.md`는 역할 2(견고성 점수 추론)를 이번 범위에서 빼되 **"라우터와 서비스 계층을 나중에 모듈만 추가하면 되도록 나눠 둔다"**고 약속했다. 지금 코드를 보고 판정한 결과를 적는다.

### 대체로 지켜졌다

| 붙일 자리 | 지금 모양 | 더할 것 |
|---|---|---|
| 라우터 | [`app/api/v1/`](app/api/v1/) 아래 도메인별 파일, `main.py`가 `include_router`로 등록 | 파일 하나와 등록 한 줄 |
| 서비스 | [`app/services/`](app/services/) 아래 관심사별 모듈 | 모듈 하나 |
| 주입 | [`app/api/deps.py`](app/api/deps.py)가 호출 가능 객체를 돌려주는 형태 | 제공자 함수 하나 |
| 오류 | 핸들러가 `DomainError` **기반 클래스**에 붙어 있다 | 예외 클래스 하나. 핸들러 등록은 그대로 |
| 설정 | [`app/core/config.py`](app/core/config.py) 한 곳에서만 환경변수를 읽는다 | 필드 추가 |

**라우터·서비스·주입·오류 넷은 실제로 모듈 추가만으로 붙는다.** 기존 파일을 고치는 것은 `main.py`의 등록 한 줄뿐이다.

### 걸리는 것 셋

**`main.py`의 lifespan이 LLM 전용이다.** 지금은 `load_prompt_templates()` 하나만 부른다. 역할 2가 모델 파일을 읽어야 하면 그 호출이 같은 자리에 붙어, 기동 준비가 파트별로 쌓이면서 `main.py`가 그 목록을 알게 된다. 두 번째가 붙는 시점에 준비 단계를 모아 두는 편이 낫다.

**`/health/ready`가 프롬프트만 본다.** [`app/api/v1/health.py`](app/api/v1/health.py)가 `prompts_loaded()` 하나로 판정한다. 모델 파일이 없어도 준비됐다고 답한다. 역할 2를 붙이면 이 판정도 같이 넓혀야 하고, **안 넓히면 준비 안 된 인스턴스가 트래픽을 받는다.**

**`app/core/schema.py`가 전략 DSL 전용이다.** 규칙상 필드명은 이 파일 하나에만 적는데, 역할 2의 요청·응답 모델을 여기 넣으면 서로 다른 도메인이 한 파일에 섞이고 따로 두면 규칙이 갈라진다. 파일을 나눌지 규칙을 "도메인마다 정의처 하나"로 읽을지는 붙이는 사람이 정해야 한다.

셋 다 모듈 추가를 막지는 않는다. **약속은 지켜졌고, 손댈 자리가 어디인지가 위 목록이다.**

### 답하지 않고 남기는 질문 — 무상태 원칙

이 서비스는 DB·Redis에 붙지 않고 필요한 데이터를 요청 본문으로 받는다. 역할 2는 그 전제를 흔들 수 있다.

- **모델 파일을 어디서 읽나.** 이미지에 굽으면 배포마다 이미지를 다시 만들고, 볼륨이나 오브젝트 스토리지에서 읽으면 이 서비스가 처음으로 외부 저장소를 알게 된다
- **점수를 매길 입력을 누가 주나.** 전략 하나만 받으면 무상태가 유지되지만, 과거 성과나 시세가 필요하면 Spring이 그것까지 담아 보내야 한다. 요청이 커진다
- **모델 버전을 응답에 담을 것인가.** `meta.model`을 밖으로 안 내보내기로 한 것과 같은 판단이 필요하다

**여기서 정하지 않는다.** 역할 2의 입력이 정해지기 전에 고르면 근거 없이 고르는 것이다. 정할 때 [`docs/DECISIONS.md`](docs/DECISIONS.md)에 남긴다.

## 문서

계획, 도메인 결정, 인계 명세는 [docs/README.md](docs/README.md)에 색인이 있다. 작업 규칙은 [CLAUDE.md](CLAUDE.md)에 있다.

**README는 실행하는 사람을 위한 문서다.** "왜 이렇게 만들었는가"는 `DECISIONS.md`, "무엇을 어느 순서로"는 `TASKS.md`, "같은 데서 또 막혔을 때"는 `TROUBLESHOOTING.md`에 있다. 여기서 요약하지 않고 가리킨다.
