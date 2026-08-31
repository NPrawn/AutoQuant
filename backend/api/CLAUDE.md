# backend/api — Java / Spring Boot

Java 21, Spring Boot 3.5.16, PostgreSQL 16 기반 REST API 서버다. 이 디렉토리의 작업에는 루트 `CLAUDE.md`와 아래 규칙을 적용한다.

## 패키지

```text
com.ssafy.a107
├── common/domain       BaseEntity 등 공통 영속 모델
├── common/exception    ApiErrorCode, ApiException, ApiExceptionHandler
├── common/exception/response
│                       ApiErrorResponse
├── candle/domain       확정 분봉 읽기 모델
├── auth                인증
└── trading             백테스트·모의투자
```

- 패키지명은 `market`이 아니라 `candle`이다.
- `common`은 기능 패키지에 의존하지 않는다.
- `candle`과 `trading`은 서로 직접 참조하지 않는다.
- 확정 분봉은 Go Consumer가 쓰고 Spring은 `@Immutable` 엔티티로 읽는다.

## DB와 JPA

- DB는 PostgreSQL이다. 일반 Spring 컨벤션의 MySQL 표기는 이 프로젝트에 적용하지 않는다.
- Flyway SQL이 스키마의 단독 원본이며 `ddl-auto=validate`를 유지한다.
- 기존 마이그레이션은 수정하지 않고 새 버전 파일을 추가한다.
- `minute_candle`의 PK는 `(market_id, interval_minutes, open_time)`이다.
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

## 검증

```bash
./gradlew build
```

테스트는 JUnit 5와 AssertJ를 사용하고 `@DisplayName`은 한국어로 작성한다. 생성물인 `bin/`과 `build/`는 직접 수정하거나 커밋하지 않는다.
