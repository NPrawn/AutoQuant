# 백엔드 테스트 컨벤션

Java Spring Boot 테스트(Controller, Service, Persistence) 작성 시 적용한다.

## 핵심 원칙

- JUnit 5, AssertJ를 사용한다.
- `@DisplayName`에 한국어로 테스트 이름을 작성한다.
- given / when / then 구조를 따른다.
- 테스트 하나에 동작 하나만 검증한다.
- 테스트 이름은 구현이 아닌 동작을 설명한다.
- private 메서드를 직접 테스트하지 않는다.
- 불필요한 Spring Context 로딩을 피한다.

## 테스트 레이어

### Controller 테스트

검증 대상: 요청 유효성, 요청·응답 매핑, HTTP 상태 코드, 에러 응답, REST Docs 스니펫

- `RestAssuredMockMvc`를 사용한다.
- Controller 테스트에서 REST Docs를 생성한다.
- Service 의존성은 Mock으로 처리한다.
- 비즈니스 로직은 검증하지 않는다.
- Repository를 직접 접근하지 않는다.
- API 계약(경로, 메서드, 헤더, 요청·응답 필드)을 검증한다.

### Service 테스트

검증 대상: 비즈니스 흐름, 트랜잭션 경계, 도메인 오케스트레이션, 예외 케이스

- 기본적으로 단위 테스트로 작성한다.
- Repository와 외부 의존성은 Mock으로 처리한다.
- 도메인 동작과 서비스 판단을 검증한다.
- 필요한 경우에만 Spring Context를 로딩한다.
- public 서비스 메서드를 통해 테스트한다.

### Persistence 테스트

검증 대상: Repository 쿼리, JPA 매핑, fetch join·EntityGraph 동작, 제약 조건, 커스텀 쿼리 메서드

- 통합 테스트로 작성한다.
- 실제 영속성 컨텍스트를 사용한다.
- 저장·로드한 데이터를 명시적으로 검증한다.
- DB 읽기 검증 시 영속성 컨텍스트를 비운다.
- Repository를 Mock으로 처리하지 않는다.
- 커스텀 Repository 메서드는 프로덕션 유스케이스가 있을 때만 추가하며, 테스트 전용 메서드와 테스트는 함께 제거한다.

## REST Docs

- 통과한 테스트에서만 REST Docs를 생성한다.
- 요청 필드, 응답 필드, Path 파라미터, Query 파라미터, 헤더가 있으면 모두 문서화한다.
- 예시 값은 실제와 유사하게 작성한다.
- 주요 실패 케이스의 에러 응답도 문서화한다.
- 문서와 실제 API 동작이 다르면 REST Docs 테스트는 실패해야 한다.

## 픽스처

- 일회성 픽스처는 테스트 코드 가까이에 둔다.
- 반복되는 픽스처는 전용 픽스처 클래스·헬퍼·팩터리 메서드로 추출한다.
- 픽스처 이름은 테스트 의도를 드러낸다.
- 테스트 데이터는 실행 순서에 의존하지 않는다.
- 객체 생성 픽스처와 영속성 헬퍼를 분리한다. 영속성 헬퍼는 `saveUser`처럼 부작용을 이름에 드러낸다.
- 중요한 테스트 조건을 픽스처 안에 숨기지 않는다.

픽스처 위치는 테스트 대상 패키지 기준으로 둔다.

```
src/main/java/com/example/order/domain/Order.java
src/test/java/com/example/order/domain/OrderFixture.java
```

같은 패키지를 선언하면 package-private 멤버에 접근할 수 있다. 픽스처를 위해 프로덕션 코드의 접근 제어자를 넓히지 않는다.

## 목(Mock)

- 테스트 대상 외부의 경계나 의존성만 Mock으로 처리한다.
- 도메인 객체, 값 객체, 단순 엔티티는 Mock으로 처리하지 않는다.
- 상호작용 자체가 검증 대상일 때만 interaction verification을 한다.
- 상태·결과 검증을 interaction verification보다 선호한다.
- Mockito 대신 BDDMockito를 사용한다.
- 스터빙은 `when(...).thenReturn(...)` 대신 `given(...).willReturn(...)` / `willThrow(...).given(...)`을 사용한다.
- 검증은 `verify(mock)...` 대신 `then(mock).should()...`을 사용한다.
- Mock 생성은 `Mockito.mock(X.class)` 대신 `@Mock`과 `@ExtendWith(MockitoExtension.class)`를 사용한다.

## 단언(Assertion)

- AssertJ를 사용한다.
- 구현 세부사항이 아닌 의미 있는 결과를 단언한다.
- 비즈니스 규칙 위반에 대한 예외 테스트를 포함한다.
- 동작을 증명하지 못하는 넓은 단언은 피한다.

## 금지 사항

- 테스트 하나에서 여러 동작 검증
- Service 단위 테스트에서 실제 DB 사용
- 실제 API 동작과 다른 REST Docs 스니펫
- 빈 픽스처, 실행 순서에 의존하는 픽스처
