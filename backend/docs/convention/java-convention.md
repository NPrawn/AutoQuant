# Java 컨벤션

## 공통 원칙

- 지정되지 않은 기술은 도입 전에 확인한다.
- DRY와 SRP를 지키고, 불필요한 import·사용하지 않는 코드·매직 스트링을 남기지 않는다.
- 변경 후 관련 빌드와 테스트를 실행한다.

## 코드 스타일과 네이밍

- 모든 파일은 마지막 개행으로 끝내고 wildcard import를 사용하지 않는다.
- 클래스·인터페이스는 UpperCamelCase이며 인터페이스에 `I` 접두어를 붙이지 않는다.
- 구현체는 `Impl` 접미사 또는 구체적인 역할 이름을 사용하고, 엔티티에는 접미사를 붙이지 않는다.
- 요청 DTO는 `XXXRequest`와 `controller/request`, 응답 DTO는 `XXXResponse`와 `controller/response` 하위 패키지를 사용한다.
- 메서드·변수는 lowerCamelCase, 메서드는 동사+명사, 상수는 UPPER_SNAKE_CASE를 사용한다.
- `cnt`, `idx`, `msg` 같은 축약어 대신 `count`, `index`, `message`처럼 의미가 드러나는 이름을 사용한다.

## 의존성 주입과 Lombok

- 생성자 주입을 사용하고 `@Autowired` 필드 주입은 금지한다.
- `@RequiredArgsConstructor`와 `private final` 필드를 사용한다.
- DTO는 Java `record`를 우선 사용한다.
- 허용 Lombok: `@Getter`, `@Builder`, 민감 필드를 제외한 `@ToString`, `@RequiredArgsConstructor`.
- 금지 Lombok: `@Data`, 엔티티의 `@Setter`.

## 예외와 상수

- 비즈니스 예외는 `RuntimeException`을 상속한 커스텀 예외 클래스로 표현한다.
- 빈 `try-catch`로 예외를 숨기지 않고, 중첩 깊이를 낮추기 위해 early return을 선호한다.
