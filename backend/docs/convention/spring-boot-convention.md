# Spring Boot 컨벤션

Java 21, Spring Boot 3.5.2, JPA/Hibernate, MySQL 기반 코드에 적용한다.

## 레이어드 아키텍처

- `controller`: 요청 처리·유효성 검증·응답 매핑, `service`: 트랜잭션 경계와 애플리케이션·도메인 로직, `repository`: DB 접근, `domain`: 엔티티 상태 변경과 비즈니스 규칙을 담당한다.
- 의존 방향은 `Controller -> Service -> Repository -> DB`, `Controller -> DTO`, `Service -> Domain Entity`만 허용한다.
- Controller는 Service만 호출하고 Repository를 직접 호출하지 않는다. Service는 Controller에 의존하지 않는다. Repository에 비즈니스 판단 로직을 두지 않는다.
- Domain Entity는 상위 레이어에 의존하지 않으며 Entity와 DTO 변환은 Service/API 경계에서 수행한다. Service 간 순환 의존은 금지한다.

## 검증 책임 분리

- DTO는 `@Valid`와 Bean Validation 어노테이션으로 null, 범위, 길이, 형식 등 요청 값 자체를 검증한다.
- Service는 권한, 중복, 현재 상태, 엔티티 간 관계 등 유스케이스 단위의 비즈니스 규칙을 검증한다.
- Domain Entity 메서드는 Service의 검증을 통과한 상태 변경을 수행한다. 동일한 유스케이스 규칙을 Domain Entity에 중복 검증하지 않는다.
- Domain Entity 자체의 불변식이 필요한 경우에만 해당 메서드에서 검증한다.

## Service CQRS와 네이밍

- 모든 Service는 CQRS(Command Query Responsibility Segregation)를 적용한다. 하나의 Service 클래스에 상태 변경과 조회 책임을 함께 두지 않는다.
- 상태 변경 책임은 `XxxCommandService`, 조회 책임은 `XxxReadService`로 분리한다.
- Command Service 메서드는 생성·수정·삭제와 같이 상태를 변경하는 동사로, Read Service 메서드는 조회 의도가 드러나는 이름으로 작성한다.
- Controller와 다른 Service는 필요한 Command Service 또는 Read Service만 의존하며, Read Service에서 상태를 변경하지 않는다.

## REST API와 예외 응답

- URL은 소문자 kebab-case 복수 명사를 사용하고 경로 변수는 camelCase를 사용한다. 예: `/order-items`, `/users/{userId}`.
- URL에 동사를 사용하지 않고 HTTP 메서드로 행위를 표현하며, 계층 관계는 `/users/{userId}/orders`처럼 경로로 표현한다.
- `@RestControllerAdvice`와 `GlobalExceptionHandler`로 API 예외 응답을 일관되게 처리한다.

## 트랜잭션과 JPA

- `@Transactional`은 메서드가 아니라 Service 클래스 레벨에 선언한다.
- `XxxCommandService`에는 `@Transactional`, `XxxReadService`에는 `@Transactional(readOnly = true)`를 클래스 레벨에 선언한다.
- 예외적인 메서드 수준 트랜잭션 설정이 필요하면 별도 클래스 분리를 우선 검토하고, 불가피한 이유를 코드 리뷰에서 설명한다.
- 엔티티 상태 변경은 `user.requestWithdrawal()`처럼 의도가 드러나는 도메인 메서드로 수행한다.
- 엔티티에 `@Table(name = "...")`을 명시하고 연관관계 기본 fetch 전략은 `FetchType.LAZY`를 사용한다.
- `@OneToMany` 사용 시 N+1을 검토하고 필요하면 `@EntityGraph` 또는 fetch join을 사용한다.
- Enum에는 `@Enumerated(EnumType.STRING)`을 사용하고 `ORDINAL`은 금지한다. `created_at`, `updated_at`은 `@MappedSuperclass`로 공유한다.

## 외부 API와 금지 사항

- 외부 HTTP API는 `WebClient`를 사용하고 `RestTemplate`은 사용하지 않는다. 외부 API 클라이언트는 인터페이스와 구현체로 분리한다.
- DB 트랜잭션 안에서 외부 HTTP 호출을 하지 않으며, 외부 호출과 트랜잭션 작업을 조율해야 하면 Facade를 사용한다.
- 확인 없이 지정되지 않은 기술을 도입하거나 Controller에 비즈니스 로직을 작성하지 않는다.
