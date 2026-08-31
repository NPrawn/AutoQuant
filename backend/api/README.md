# backend/api

> Spring Boot API 서버. Java 21 · Spring Boot 3.5.16 · Gradle 단일 프로젝트.

Gradle이 소유하는 디렉토리는 여기까지다. 형제인 `../ingest/`는 Go 모듈이며 이 빌드와 의존성이 없다.

## 패키지 구성

```
src/main/java/com/ssafy/a107/
├─ A107Application.java     @SpringBootApplication   :8080
├─ common/                  공통 엔티티 · 예외 처리
├─ auth/                    JWT 발급 · 검증
├─ candle/                  확정 분봉 도메인 · 조회
└─ trading/                 모의투자 · 백테스팅

src/main/resources/
├─ application.yml
└─ db/migration/            Flyway — DB 스키마의 단독 원본
```

## 패키지 경계

모듈이 하나라 컴파일러가 경계를 강제하지 않는다. **패키지 규칙으로 지킨다.**

| 패키지 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `common` | 공통 엔티티, 예외 코드, 전역 예외 처리 | 기능별 비즈니스 로직 |
| `auth` | JWT 발급·검증, 비밀번호 해싱 | 도메인 로직, 컨트롤러 |
| `candle` | 확정 분봉 엔티티·조회 | 시세 수집, Redis·Kafka 쓰기 |
| `trading` | 모의투자 주문·체결, 백테스팅 엔진 | 시세 수집, 랭킹 |

### 의존 방향

```
candle ──┐
         ├──> common
trading ─┘
auth ────────> common
```

- **`common`은 기능 패키지에 의존하지 않는다.**
- **`candle` ↔ `trading` 상호 참조 금지.** 공유가 필요하면 `common` 중 맞는 곳으로 내린다.
- **확정 분봉 쓰기는 Go consumer 전용이다.** JVM이 쓰면 데이터 출처가 갈라진다.

## 규칙

1. **`ddl-auto`는 `validate`.** Go consumer가 같은 테이블에 INSERT하므로 Hibernate가 스키마를 변경하면 안 된다.
2. **DB 스키마는 Flyway가 단독 소유.** 적용된 마이그레이션 파일은 수정하지 않고 `V{n}__*.sql`을 추가한다.
3. **`common`은 기능 패키지에 의존하지 않는다.**
4. **`candle`과 `trading`은 서로를 참조하지 않는다.**
5. **확정 분봉은 Go consumer만 쓴다.**

## 명령

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun    # :8080
./gradlew dependencies --configuration compileClasspath
```
