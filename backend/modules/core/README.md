# modules/core

> 도메인의 정본(source of truth). 엔티티 · 리포지토리 · DB 스키마 · 캐시 접근을 소유한다.

두 API 앱이 공유하는 유일한 모듈이다. 의존성 그래프의 leaf이며, 어떤 내부 모듈도 의존하지 않는다.

## 책임

- **JPA 엔티티 · 리포지토리** — 도메인 모델과 조회·저장
- **값객체 / record** — 계층 간 전달용 불변 타입
- **DB 스키마** — `src/main/resources/db/migration/` 의 Flyway 마이그레이션 SQL
- **인증** — JWT 발급·검증, 비밀번호 해싱
- **공통 예외 · 응답 포맷** — 두 앱이 같은 에러 규격을 쓰도록
- **`cache` 패키지** — Go consumer가 Redis에 써둔 데이터를 읽는 유일한 경로

## 담지 않는 것

| 담지 않음 | 대신 어디로 |
|---|---|
| 서비스 · 유스케이스 로직 | 각 앱 (`apps/api-market`, `apps/api-trading`) |
| REST 컨트롤러 · DTO 변환 | 각 앱 |
| Redis **쓰기** | `backend/ingest` (Go consumer 전용) |
| Kafka 관련 코드 | `backend/ingest` (JVM에는 존재하지 않음) |
| 외부 API 클라이언트 (Upbit, AI) | 각 앱 또는 `backend/ingest` |

**이 경계가 이 모듈의 핵심이다.** `core`가 비즈니스 로직을 흡수하기 시작하면 두 앱이 한 덩어리로 묶여, 어느 기능을 고쳐도 전체가 재빌드된다. 엔티티 · 리포지토리 · 값객체 · 캐시 접근까지만 넣는다.

## `cache` 패키지

Redis는 도메인의 정본이 아니라 Go 파이프라인이 만들어내는 파생 계층이다. 이 패키지에 격리하는 이유는 **다른 언어의 출력 포맷에 묶여 있는 코드를 한 곳에 모으기 위함**이다.

- 캐시 키 규칙을 상수로 정의한다 (문자열 리터럴을 앱에 흩뿌리지 않는다)
- Redis JSON을 값객체로 역직렬화한다 (JPA 엔티티로 역직렬화하지 않는다)
- **읽기 전용을 유지한다.** 쓰기 메서드를 추가하지 않는다

## 의존성

- **내부**: 없음 (leaf)
- **외부**: `spring-boot-starter-data-jpa` (`api`), `spring-boot-starter-data-redis` (`implementation`), `flyway-core`, `flyway-database-postgresql`, `postgresql`

Redis 스타터가 `implementation`인 이유는 `cache` 패키지 내부에서만 쓰기 때문이다. 앱은 `RedisTemplate`을 직접 컴파일 참조할 수 없다.

## 규칙

1. **`ddl-auto`는 `validate`.** `update`로 두면 Hibernate가 스키마를 변경하는데, Go consumer가 같은 테이블에 INSERT하므로 절대 안 된다.
2. **스키마 변경은 반드시 마이그레이션 SQL로.** `V{n}__{설명}.sql`을 추가한다. 적용된 마이그레이션 파일은 수정하지 않는다.
3. **테이블 스키마와 Redis 키/값 포맷은 Go 쪽과 공유되는 계약이다.** 컴파일러가 잡아주지 못하므로, 변경 시 `backend/ingest`를 같은 커밋에서 함께 수정한다.
4. **어떤 내부 모듈도 의존하지 않는다.**
