# AI 전략 변환 서비스

자연어로 설명한 투자 전략을 검증 가능한 전략 DSL(JSON)로 바꾸는 FastAPI 서비스다.

## 서비스 경계

Spring 백엔드가 HTTP로 이 서비스를 호출한다. 호출 방향은 한쪽뿐이다.

```text
Spring 백엔드 -> AI 서비스
```

이 서비스는 DB나 Redis에 직접 연결하지 않고, 사용자를 인증하거나 시세를 수집하지도 않는다. 필요한 데이터는 Spring이 요청에 담아 전달한다.

## 구성

- Python 3.11 이상
- FastAPI, Uvicorn
- Pydantic v2, pydantic-settings
- SSAFY GMS 게이트웨이를 거치는 OpenAI SDK
- pytest, httpx

## 로컬 실행

```bash
python -m pip install -r requirements.txt
uvicorn app.main:app --reload
```

로컬 서버는 8000 포트와 기본 loopback 바인딩을 사용한다. 컨테이너 배포 범위가 아닌 이상 `--host 0.0.0.0`으로 외부에 노출하지 않는다.

## 환경변수

환경변수 설정은 A-2에서 추가한다. 사용할 항목은 `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`, `LLM_TIMEOUT_SECONDS`, `MAX_RETRIES`, `LOG_LEVEL`, `APP_ENV`다.
