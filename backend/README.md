# backend

Java 21 · Spring Boot 3.5.16 API 서버와 Go 수집 파이프라인. 두 빌드 도구가 각자 서브트리를 소유한다.

| 서브트리 | 빌드 도구 | 내용 |
|---|---|---|
| `src/` | Gradle (단일 프로젝트) | Spring Boot 앱 하나 |
| `ingest/` | Go modules | Go 바이너리 3개 (코드 미작성) |

`settings.gradle`에는 포함할 서브프로젝트가 없다. Gradle은 `ingest/`의 존재를 모른다.

## 구성

```
backend/
├─ build.gradle
├─ settings.gradle
├─ src/main/
│  ├─ java/com/ssafy/a107/
│  │  ├─ A107Application.java     @SpringBootApplication  :8080
│  │  ├─ domain/                  엔티티 · 값객체 · 리포지토리
│  │  ├─ cache/                   Redis 읽기 — Go↔JVM 계약 표면
│  │  ├─ auth/                    JWT 발급 · 검증
│  │  ├─ market/                  랭킹 · 체결 · 전략수립
│  │  └─ trading/                 모의투자 · 백테스팅
│  └─ resources/
│     ├─ application.yml
│     └─ db/migration/            Flyway — DB 스키마의 단독 원본
└─ ingest/                        Go 수집 파이프라인
   └─ cmd/{producer,consumer,backfill}
```

## 왜 단일 앱인가

랭킹 API와 모의투자 API를 별도 Spring Boot 앱으로 쪼갰다가 되돌렸다. 두 쪽이 **DB 스키마 · 도메인 모델 · 인증을 전부 공유**하므로 애초에 독립 서비스가 아니었고, 쪼갠 대가만 남았다:

- Flyway를 한쪽 앱에서만 켜야 하는 비대칭 (둘 다 켜면 부팅 시 경합)
- 프론트가 CORS를 두 번 겪고 dev 프록시가 두 개
- "두 앱이 공유하는 모듈에 뭘 넣나"라는 답 없는 논쟁 — 그 모듈이 잡동사니 버킷이 된다

**배포 단위를 쪼갤 근거가 관측되면 그때 쪼갠다.** 근거란 예를 들어 백테스팅이 CPU를 오래 점유해 조회 API를 굶기는 현상이다. 아래 패키지 경계를 지켜두면 그때 `trading`을 별도 프로세스로 떼내는 비용이 작다.

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

## Go↔JVM 계약

`ingest/`는 Gradle 빌드와 의존성이 0이다. 결합은 런타임에만 있고 접점은 두 개다.

| 계약 | 쓰는 쪽 | 읽는 쪽 |
|---|---|---|
| Redis 키/값 포맷 | `ingest/internal/redis` | `cache` 패키지 |
| DB 스키마 | `ingest/internal/store` (INSERT만) | `domain` 패키지 |

**컴파일러가 잡아주지 못하는 계약이다.** 한쪽만 바꾸면 런타임에 조용히 깨지므로 항상 같은 커밋에서 양쪽을 함께 수정한다. 스키마 변경은 `db/migration`에 SQL을 추가한 뒤 Go 쪽 쿼리를 맞춘다.

## 규칙

1. **`ddl-auto`는 `validate`.** Go consumer가 같은 테이블에 INSERT하므로 Hibernate가 스키마를 변경하면 안 된다.
2. **DB 스키마는 Flyway가 단독 소유.** 적용된 마이그레이션 파일은 수정하지 않고 `V{n}__*.sql`을 추가한다.
3. **`domain`은 leaf를 유지한다.**
4. **`market`과 `trading`은 서로를 참조하지 않는다.**
5. **`cache`에 쓰기 메서드를 만들지 않는다.**
6. **`ingest`는 Gradle 빌드에 포함하지 않는다.**

## 명령

```bash
./gradlew build            # 컴파일 + 테스트
./gradlew bootRun          # :8080
./gradlew dependencies --configuration compileClasspath

cd ingest && go build ./... && go vet ./...
```
