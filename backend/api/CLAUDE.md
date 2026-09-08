# backend/api — Java / Spring Boot

Java 21, Spring Boot 3.5.16, PostgreSQL 16 기반 REST API 서버다. 이 디렉토리의 작업에는 루트 `CLAUDE.md`와 아래 규칙을 적용한다.

## 패키지

```text
com.ssafy.a107
├── common/domain       BaseEntity 등 공통 영속 모델
├── common/exception    ApiErrorCode, ApiException, ApiExceptionHandler
├── common/exception/response
│                       ApiErrorResponse
├── common/logging      RequestIdFilter (요청 식별자 MDC 전파)
├── member/domain       서비스 회원 계정
├── asset/controller    관리자용 종목 등록 API
├── asset/service       외부 검증 조율과 종목 등록 트랜잭션
├── asset/repository    Asset·수집 대상 DB 접근
├── asset/infrastructure/upbit
│                       Upbit 공개 페어 목록 Client
├── asset/domain        거래소의 거래 자산·수집 대상 기준 정보
├── candle/domain       확정 분봉 읽기 모델
├── realtime/domain     실시간 체결·진행 봉 이벤트
├── realtime/service    실시간 이벤트 발행 인터페이스
├── realtime/infrastructure/redis
│                       Redis 채널 구독·수신 DTO·검증
├── realtime/infrastructure/websocket
│                       STOMP 설정·구독 정책·종목별 발행
├── realtime/controller/response
│                       WebSocket 응답 DTO
├── ai/controller       전략 설명 변환 API
├── ai/service          회원당 호출 한도와 변환 조율
├── ai/infrastructure   ai/ FastAPI 서버 Client
├── auth                인증
└── trading             백테스트·모의투자
```

- 패키지명은 `market`이 아니라 `candle`이다.
- `common`은 기능 패키지에 의존하지 않는다.
- `member`는 서비스 회원을, `asset`은 거래소의 거래 자산을 표현한다.
- `asset`을 회원 보유 잔액이나 포지션 의미로 사용하지 않는다.
- `candle`과 `trading`은 서로 직접 참조하지 않는다.
- `ai`는 `strategy/domain/dsl`을 그대로 쓴다. 전략 DSL 타입을 `ai` 안에 다시 만들지 않는다.
- AI 서버의 오류 형식(`code`·`message`·`detail`)은 밖으로 흘리지 않고 `ApiErrorCode`로 옮긴다.
- 외부 서버를 부를 때 `X-Request-ID`를 같이 보낸다. 값은 `RequestIdFilter.current()`가 들고 있다.
- 확정 분봉은 Go Consumer가 쓰고 Spring은 `@Immutable` 엔티티로 읽는다.
- `AssetTradingStatus`는 거래소 거래지원 상태이고 `MarketDataCollectionStatus`는 우리 서비스의 수집 희망 상태다. 두 상태를 합치지 않는다.
- 종목 등록 API의 `ENABLED`는 실제 Go WebSocket 구독 완료를 의미하지 않는다.
- `realtime`은 Redis 수신 이벤트를 WebSocket으로 중계한다. DB 조회·쓰기와 봉 집계를 하지 않는다.
- Redis 수신 adapter는 `RealtimeEventPublisher`에 의존하고 WebSocket adapter를 직접 참조하지 않는다.
- `trade:realtime:updates`와 `candle:1m:live:updates`는 인스턴스마다 한 번씩 구독한다. Go의 snake_case JSON을 검증하고 종목별 camelCase 응답으로 변환한다.
- `/ws`는 기존 JWT HTTP 인증과 same-origin 정책을 유지한다. STOMP는 종목별 SUBSCRIBE/UNSUBSCRIBE만 제공하며 클라이언트 SEND를 허용하지 않는다.
- 실시간 스트림은 best-effort이며 초기 데이터·재연결 복구·재집계를 담당하지 않는다. WebSocket 계약은 README에 기록하고 REST Docs/OpenAPI에는 넣지 않는다.

## DB와 JPA

- DB는 PostgreSQL이다. 일반 Spring 컨벤션의 MySQL 표기는 이 프로젝트에 적용하지 않는다.
- Flyway SQL이 스키마의 단독 원본이며 `ddl-auto=validate`를 유지한다.
- 기존 마이그레이션은 수정하지 않고 새 버전 파일을 추가한다.
- `minute_candle`의 PK는 `(asset_id, interval_minutes, open_time)`이다.
- Java 매핑과 Flyway의 타입·nullable·precision·scale·제약조건을 함께 맞춘다.
- 엔티티에 setter를 두지 않고 상태 변경이 필요하면 의도가 드러나는 도메인 메서드를 사용한다.

## API 오류

- 오류 정의는 `common/exception/ApiErrorCode`에 추가한다.
- 코드는 `{DOMAIN}-{HTTP_STATUS}-{SEQUENCE}` 형식으로 중복 없이 관리한다.
- enum 상수는 도메인별 `//` 주석 구역으로 나눈다.
- 예상 가능한 오류는 `ApiException`을 사용한다.
- 컨트롤러에서 개별 `try/catch`로 오류 응답을 만들지 않는다.
- 내부 예외, SQL, 스택 트레이스, 요청의 민감 값은 API 응답에 노출하지 않는다.

## 요청·응답 DTO 패키지

- 기능 API의 요청 DTO는 `{feature}/controller/request` 하위에 `XxxRequest`로 둔다.
- 기능 API의 응답 DTO는 `{feature}/controller/response` 하위에 `XxxResponse`로 둔다.
- 공통 오류 응답은 `common/exception/response` 하위에 둔다.
- `request`와 `response` DTO를 같은 패키지에 섞거나 도메인 엔티티 패키지에 두지 않는다.
- Controller는 요청 DTO를 받고 응답 DTO를 반환하며 JPA 엔티티를 API 계약으로 직접 노출하지 않는다.

## 컨벤션

아래 문서를 함께 따른다.

- `../docs/convention/java-convention.md`
- `../docs/convention/spring-boot-convention.md`
- `../docs/convention/jpa-convention.md`
- `../docs/convention/back-test-convention.md`

외부 API용 `WebClient`는 이름을 부여한 Bean으로 주입한다. 이 프로젝트에서는 `WebClient.create(baseUrl)`로 생성하고 `WebClient.Builder` 주입이나 `WebClient.builder()` 호출을 사용하지 않는다.

## 검증

```bash
./gradlew build
```

테스트는 JUnit 5와 AssertJ를 사용하고 `@DisplayName`은 한국어로 작성한다. 생성물인 `bin/`과 `build/`는 직접 수정하거나 커밋하지 않는다.

Controller 테스트는 `RestAssuredMockMvc`로 작성한다. 문서화 대상 테스트는 REST Docs 리소스 스니펫을 생성하고 `./gradlew openapi3`으로 `build/api-spec/openapi.yaml`을 만든다.
