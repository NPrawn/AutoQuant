# apps/api-market

> 랭킹 · 체결 · 전략수립 · 로그인을 제공하는 REST API. **Flyway 마이그레이션 실행 주체.**

포트 `8080`.

## 책임

- **랭킹 API** — Redis ZSET 기반 수익률 · 거래량 랭킹 조회
- **체결 API** — 실시간 체결 내역 조회
- **전략수립 API** — `ai/` FastAPI 서비스를 호출해 전략 추론 결과를 중계
- **인증** — 로그인 / 회원가입 / 토큰 재발급 (`core`의 JWT 컴포넌트 사용)
- **DB 마이그레이션 실행** — 부팅 시 Flyway가 `core`의 마이그레이션 SQL을 적용

## 담지 않는 것

| 담지 않음 | 대신 어디로 |
|---|---|
| 모의투자 주문 · 백테스팅 | `apps/api-trading` |
| 엔티티 · 리포지토리 정의 | `modules/core` |
| Redis 키 규칙 · 역직렬화 | `modules/core` 의 `cache` 패키지 |
| 시세 수집 · 적재 | `backend/ingest` |

## 의존성

- **내부**: `modules/core`
- **외부**: `spring-boot-starter-web`
- **네트워크**: `ai/` FastAPI 서비스를 HTTP로 호출

## 규칙

1. **`flyway.enabled: true` — 이 앱만.** 두 앱이 모두 `core`에 의존하므로, 둘 다 부팅 시 마이그레이션하면 경합한다. `api-trading`은 비활성이다.
2. **`jpa.hibernate.ddl-auto: validate`.** 스키마는 마이그레이션 SQL이 단독 소유한다.
3. **`api-trading`을 의존하지 않는다.** 공유가 필요하면 `core`로 올린다.
