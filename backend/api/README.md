# backend/api

> Spring Boot API 서버. Java 21 · Spring Boot 3.5.16 · Gradle 단일 프로젝트.

Gradle이 소유하는 디렉토리는 여기까지다. 형제인 `../ingest/`는 Go 모듈이며 이 빌드와 의존성이 없다.

## 패키지 구성

```
src/main/java/com/ssafy/a107/
├─ A107Application.java     @SpringBootApplication   :8080
├─ domain/                  엔티티 · 값객체 · 리포지토리
├─ cache/                   Redis 읽기 — Go↔JVM 계약 표면
├─ auth/                    JWT 발급 · 검증
├─ market/                  랭킹 · 체결 · 전략수립
└─ trading/                 모의투자 · 백테스팅

src/main/resources/
├─ application.yml
└─ db/migration/            Flyway — DB 스키마의 단독 원본
```

## 패키지 경계

모듈이 하나라 컴파일러가 경계를 강제하지 않는다. **패키지 규칙으로 지킨다.**

| 패키지 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `domain` | 엔티티, 값객체, 리포지토리 | 서비스 로직, 컨트롤러, 캐시·외부 API 접근 |
| `cache` | Redis 키 규칙, 역직렬화, 읽기 전용 접근자 | **Redis 쓰기**(Go 전용), 비즈니스 판단, JPA |
| `auth` | JWT 발급·검증, 비밀번호 해싱 | 도메인 로직, 컨트롤러 |
| `market` | 랭킹·체결·전략수립 컨트롤러·서비스, AI 호출 | 모의투자·백테스팅 |
| `trading` | 모의투자 주문·체결, 백테스팅 엔진 | 시세 수집, 랭킹 |

### 의존 방향

```
market ──┐
         ├──> auth ──┐
trading ─┘           ├──> domain
         └──> cache ─┘
```

- **`domain`은 어떤 패키지도 의존하지 않는다.** leaf를 유지한다.
- **`market` ↔ `trading` 상호 참조 금지.** 공유가 필요하면 `domain`·`auth`·`cache` 중 맞는 곳으로 내린다.
- **`cache`는 읽기 전용.** 쓰기는 Go consumer 전용이다. JVM이 쓰면 데이터 출처가 갈라진다.

## 규칙

1. **`ddl-auto`는 `validate`.** Go consumer가 같은 테이블에 INSERT하므로 Hibernate가 스키마를 변경하면 안 된다.
2. **DB 스키마는 Flyway가 단독 소유.** 적용된 마이그레이션 파일은 수정하지 않고 `V{n}__*.sql`을 추가한다.
3. **`domain`은 leaf를 유지한다.**
4. **`market`과 `trading`은 서로를 참조하지 않는다.**
5. **`cache`에 쓰기 메서드를 만들지 않는다.**

## 명령

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun    # :8080
./gradlew dependencies --configuration compileClasspath
```
