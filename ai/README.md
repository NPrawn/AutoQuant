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

**선택 셋** — 기본값이 있다.

| 이름 | 기본값 |
|---|---|
| `LLM_TIMEOUT_SECONDS` | 30 |
| `MAX_RETRIES` | 2 |
| `LOG_LEVEL` | INFO |

선택 항목을 `.env`에서 **빈 값으로 두면 안 된다.** 주석 처리하거나 값을 넣는다. `LLM_TIMEOUT_SECONDS=`처럼 비워 두면 빈 문자열이 정수 파싱에 걸려 기동이 실패하고, `LOG_LEVEL=`은 예외 없이 기본값 `INFO`를 빈 문자열로 덮어쓴다.

환경변수를 읽는 파일은 `app/core/config.py` 하나다. 다른 파일에서 `os.environ`을 직접 읽지 않는다.

**공급자를 바꾼다는 것은 `LLM_BASE_URL`을 바꾼다는 뜻이다.** GMS가 upstream 호스트를 경로에 그대로 끼우는 구조라 Anthropic은 `.../gmsapi/api.anthropic.com`, Gemini는 `.../gmsapi/generativelanguage.googleapis.com`으로 간다. 코드에 공급자 이름으로 분기하는 문자열을 두지 않는다.

## 엔드포인트

| 경로 | 응답 |
|---|---|
| `GET /health` | 200 `{"status": "ok"}` |
| `GET /health/ready` | 프롬프트 파일이 있으면 200, 없으면 503 |

**지금 `/health/ready`가 503인 것은 정상이다.** `app/prompts/` 아래에 `.txt`가 아직 없어서다. 프롬프트가 들어가면 200이 된다. 장애가 아니다.

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
| 생성된 DSL이 스펙 위반 | 422 | `DSL_VALIDATION_FAILED` |
| 요청 본문이 잘못됨 | 400 | `INVALID_REQUEST` |
| 그 밖 | 500 | `INTERNAL_ERROR` |

요청 검증 실패의 `detail`에는 필드 경로와 사유만 담는다. 보낸 값은 되돌려주지 않는다.

500 응답의 `message`는 고정 문구다. 실제 예외와 스택 트레이스는 로그에만 남는다.

## 테스트

```bash
python -m pytest
```

**GMS 키 없이 전부 돈다.** 실제 LLM API를 치는 테스트가 없다. 필요한 환경변수는 테스트가 직접 주입한다.

## 문서

계획, 도메인 결정, 인계 명세는 [docs/README.md](docs/README.md)에 색인이 있다. 작업 규칙은 [CLAUDE.md](CLAUDE.md)에 있다.
