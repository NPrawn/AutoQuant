# ai

> FastAPI 기반 전략 추론 서비스. **미구현 — 플레이스홀더.**

## 책임 (예정)

- 시세 · 캔들 데이터를 입력받아 매매 전략을 추론
- `apps/api-market`이 HTTP로 호출하는 내부 서비스

## 호출 방향

```
apps/api-market  ──HTTP──>  ai (FastAPI)
```

**단방향이다.** AI 서비스가 Spring 앱을 호출하지 않는다. 데이터가 필요하면 요청 페이로드로 전달받는다.

## 담지 않는 것

| 담지 않음 | 대신 어디로 |
|---|---|
| DB · Redis 직접 접근 | `api-market`이 조회해서 요청에 담아 전달 |
| 사용자 인증 | `apps/api-market` |
| 시세 수집 | `backend/ingest` |

## 예정 스택

Python 3.12+, FastAPI, uvicorn

## 다음 단계

- [ ] Python 설치 및 가상환경 구성
- [ ] FastAPI 프로젝트 초기화
- [ ] 전략 추론 인터페이스 정의 (요청/응답 스키마)
