# apps/api-trading

> 모의투자 주문 · 체결과 백테스팅 실행 API.

포트 `8081`.

## 책임

- **모의투자** — 주문 접수, 체결 처리, 포지션 · 잔고 · 손익 계산
- **백테스팅** — 과거 캔들 데이터로 전략을 재생하고 성과 지표 산출
- **결과 조회** — 거래 내역, 수익률, 백테스팅 리포트

체결 시 현재가는 `core`의 `cache` 패키지로 Redis에서 읽고, 캔들 히스토리는 `core`의 리포지토리로 DB에서 읽는다.

## 담지 않는 것

| 담지 않음 | 대신 어디로 |
|---|---|
| 시세 수집 · 적재 | `backend/ingest` |
| 랭킹 · 전략수립 · 인증 | `apps/api-market` |
| 엔티티 · 리포지토리 정의 | `modules/core` |
| DB 스키마 마이그레이션 | `modules/core` (실행은 `api-market`) |

## 의존성

- **내부**: `modules/core`
- **외부**: `spring-boot-starter-web`

## 규칙

1. **`flyway.enabled: false`.** 마이그레이션은 `api-market`이 단독 실행한다. 여기서 켜면 부팅 시 경합이 발생한다.
2. **`jpa.hibernate.ddl-auto: validate`.**
3. **`api-market`을 의존하지 않는다.** 인증 검증이 필요하면 `core`의 JWT 컴포넌트를 쓴다.
4. **백테스팅 전략 로직은 이 앱에 둔다.** `core`로 올리면 `api-market`까지 재빌드 대상이 된다.
