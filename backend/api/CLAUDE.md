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
├── candle/controller   종목·간격별 최근 봉 조회 API (request·response 분리)
├── candle/service      캐시 조회 조율과 DB 읽기 전용 트랜잭션
├── candle/repository   확정 분봉 조회 전용 JPA Repository
├── candle/infrastructure/redis
│                       Go Sorted Set 조회·빈 시간대 캐시 보충
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
├── notification/domain 알림과 알림 종류
├── notification/repository
│                       회원별 알림 조회
├── notification/service 알림 저장·읽음 처리와 커밋 뒤 발행
├── notification/controller
│                       알림 조회·읽음 API (response 분리)
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
- 확정 봉의 `lastEventTimeUnixMilli`는 REST 보정 후 마지막 틱 저장 시각으로 봉 종료를 넘을 수 있다. 구간은 시작 시각·간격으로 판단하며, live candle의 체결 시각 검증과 혼용하지 않는다.
- 최근 봉 조회는 `RecentMinuteCandleFacade`가 cache-aside를 조율하고 `MinuteCandleReadService`만 DB 읽기 전용 트랜잭션을 연다. 캐시 hit 시 DB 트랜잭션을 열지 않는다.
- 간격은 `candle/domain/CandleIntervals`의 1·2·5·15·60·240·1440분만 지원한다. UTC 버킷이며 `/candles/{intervalMinutes}m`으로 조회한다. 기존 `/1m`도 유지한다.
- `candle:{intervalMinutes}m:recent:{assetId}`는 Go의 snake_case JSON과 시작 시간 millisecond score를 사용한다. Spring은 DB 조회 결과로 빈 시간대만 원자적으로 보충하고 종목·간격별 최신 1,000개를 유지한다. 기존 값의 보정·갱신은 Go가 담당한다.
- 캐시가 없거나 요청 개수보다 적거나 손상/장애가 있으면 DB의 해당 종목·간격 최대 1,000개를 조회한다. Spring에서 1분봉 재집계는 하지 않는다. 진행 봉은 REST 응답에 포함하지 않는다.
- `GET /candles/coverage`는 수집 종목 전체를 합친 봉 보유 구간을 준다. 화면이 종목별 최근 봉을 받아 구간을 추정하지 않는다. 최근 봉 조회는 1,000개가 상한이라 과거가 쌓일수록 시작일이 잘린다.
- 보유 구간의 대상 종목은 `CollectedAssetReadService` 기준이라 백테스트 후보군과 같다. 화면이 고른 기간을 엔진이 그대로 평가할 수 있어야 한다.
- 보유 구간은 집계 대신 양 끝 봉을 한 건씩 읽는다. PK 앞자리가 종목이라 종목 수가 늘어도 비용이 같다.
- `GET /assets/{assetId}/factors`는 전략 엔진이 쓰는 `Ta4jFactorCalculator`를 그대로 불러 화면에 같은 값을 준다. 표시용 지표를 여기에 넣지 않는다.
- 팩터 기간은 `AssetFactorReadService`가 정한다. 요청으로 바꿀 수 없고 응답에 실어 화면이 기간을 다시 정의하지 않게 한다.
- 팩터 조회는 ta4j BarSeries 가 필요해 `MinuteCandleReadService.findRecentCandles`로 엔티티를 읽는다. 표시용 스냅샷 캐시 경로와 섞지 않는다.
- 봉이 기간을 채우지 못한 팩터는 값을 비워 반환한다. 조회는 실패가 아니며 전체를 오류로 만들지 않는다.
- 전략 후보군은 `CollectedAssetReadService` 가 정한다. 거래지원 기간에 더해 `collection_status = ENABLED` 인 종목만 남긴다. 수집을 끊은 종목은 캔들이 그 시점에서 멈춰 같은 기준으로 순위를 매길 수 없다.
- 백테스트와 모의투자가 같은 후보군을 쓴다. 한쪽만 규칙을 바꾸지 않는다.
- 수집 목록을 바꾸면 과거 구간 결과도 달라진다. 오늘 고른 종목으로 과거를 재는 생존 편향이 생기는 것을 알고 쓴다.
- `AssetTradingStatus`는 거래소 거래지원 상태이고 `MarketDataCollectionStatus`는 우리 서비스의 수집 희망 상태다. 두 상태를 합치지 않는다.
- 거래지원 시작일은 Upbit 가 주지 않아 가장 오래된 일봉 날짜로 갈음한다. 등록 때 채우고 실패하면 비워 둔다. 이 값이 비면 과거 수집이 상장 전 구간을 계속 요청하고 백테스트 후보군이 상장 전에도 그 종목을 넣는다.
- 종목 등록 API의 `ENABLED`는 실제 Go WebSocket 구독 완료를 의미하지 않는다.
- `GET /assets`는 AssetReadService의 읽기 전용 트랜잭션으로 등록된 Upbit KRW TRADING 종목을 조회한다. `assetId`·`marketCode`·한글명만 응답하며 실제 수집 연결 여부는 판단하지 않는다.
- 전략 공개 여부는 주인만 바꾼다. 복제 대상은 내 전략이거나 공개된 전략이며, 그 밖에는 권한 없음이 아니라 찾을 수 없음으로 답한다. 권한 없음으로 답하면 그 번호에 전략이 있다는 사실이 새어 나간다.
- 복제본은 DSL 만 물려받고 공개 여부는 물려받지 않는다. 남의 공개 전략을 복제한 순간 내 이름으로 다시 공개되면 뜻하지 않게 퍼진다.
- 대회 점수는 남들과 견주지 않는 절대값이다. 제출하면 그 자리에서 데이터셋 4벌을 돌리고, 백테스트가 끝나면 점수가 확정된다. 순위만 그때까지 채점된 제출 사이의 자리라 마감 전에는 잠정이다.
- 교체 제출이면 이전 평가와 결과를 버린다. 마지막 제출만 채점 대상이다.
- 점수 계산은 `ContestScoreCalculator` 한 곳에만 둔다. 같은 규칙의 참고 구현이 `ai/scripts/contest_sim/scoring.py` 라 한쪽을 바꾸면 다른 쪽도 바꾼다.
- 국면 점수는 그 데이터셋의 시장 낙폭과 견준다. 시장 낙폭은 봉 간격마다 다르니 대회 백테스트의 timeframe 으로 `MarketDataset.getMarketMaximumDrawdown` 을 고른다. 전략을 나중에 고쳐도 제출 때 돌린 백테스트의 간격이 기준이다.
- 결과와 순위는 마감 전에도 연다. 채점용 데이터의 출처(뽑은 주·시드·원본 구간)만 종료 후에 연다 (ADR-0010).
- 내 결과의 장세별 자산 곡선도 종료 후에만 싣는다. 일별 곡선으로 뽑은 주를 역산할 수 있다. 대회 백테스트 ID 는 내보내지 않는다. 체결 조회로 종목별 가격 경로가 드러난다.
- 대회 응답의 참가자 이름은 `ContestNickname.visibleTo` 를 거친다. 회원 이름이 SSAFY 실명이라 다른 참가자는 가리고 내 제출만 그대로 둔다. 순위표 검색에 참가자 이름을 다시 넣지 않는다. 가린 실명이 검색으로 드러난다.
- 알림은 백테스트 완료, 백테스트 실패, 모의투자 자동 중단 세 가지에서만 생긴다. 사용자가 직접 멈춘 모의투자는 이미 아는 일이라 알리지 않는다.
- 알림 발행은 `NotificationRequestedEvent` 를 띄우고 `@TransactionalEventListener` 의 `AFTER_COMMIT` 이 받는다. 커밋 전에 남기면 되돌아간 일을 알리게 된다.
- 알림 저장 실패는 백테스트·모의투자 트랜잭션을 되돌리지 않는다. 리스너가 잡아 로그만 남긴다.
- 알림 본문에 내부 예외 이름과 메시지를 넣지 않는다. 사용자가 다음에 할 수 있는 일을 적는다.
- 알림이 가리키는 화면 경로는 서버가 정하지 않는다. 종류와 `targetId` 만 주고 경로는 프론트가 만든다.
- `realtime`은 Redis 수신 이벤트를 WebSocket으로 중계한다. DB 조회·쓰기와 봉 집계를 하지 않는다.
- Redis 수신 adapter는 `RealtimeEventPublisher`에 의존하고 WebSocket adapter를 직접 참조하지 않는다.
- `trade:realtime:updates`와 `candle:{intervalMinutes}m:live:updates`(지원 7개 간격)는 인스턴스마다 한 번씩 구독한다. Go JSON의 채널·간격·UTC 경계를 검증하고 종목·간격별 camelCase 응답으로 변환한다. `realtime`은 간격 계약만 `candle/domain`에서 참조하며 집계·조회 서비스에 의존하지 않는다.
- `/ws`는 기존 JWT HTTP 인증과 same-origin 정책을 유지한다. STOMP는 종목별 SUBSCRIBE/UNSUBSCRIBE만 제공하며 클라이언트 SEND를 허용하지 않는다.
- 실시간 스트림은 best-effort이며 초기 확정 봉은 candle 조회 API가 담당한다. 진행 봉 스냅샷 복구·재집계는 realtime 범위가 아니다. WebSocket 계약은 README에 기록하고 REST Docs/OpenAPI에는 넣지 않는다.

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
