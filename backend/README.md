# backend

Java 21 기반 API 서버와 Go 기반 수집 파이프라인. 두 빌드 시스템이 각자의 서브트리를 소유한다.

| 서브트리 | 빌드 도구 | 내용 |
|---|---|---|
| `apps/`, `modules/` | Gradle (Groovy) | Java 21 · Spring Boot 3.5.16 |
| `ingest/` | Go modules | Go 바이너리 3개 (아직 초기화 전) |

`ingest/`는 `settings.gradle`에 포함되지 않는다. Gradle은 `ingest/`의 존재를 모른다.

## 구성

```
backend/
├─ apps/                  실행 가능한 Spring Boot 앱
│  ├─ api-market/           랭킹 · 체결 · 전략수립 · 로그인    :8080
│  └─ api-trading/          모의투자 · 백테스팅               :8081
├─ modules/               JVM 공유 라이브러리 (실행 불가)
│  └─ core/                 도메인 · JPA · DB스키마 · 인증 + cache(Redis 접근)
└─ ingest/                Go 수집 파이프라인
   └─ cmd/{producer,consumer,backfill}
```

`apps/`와 `modules/`를 나눈 이유는 **실행 단위와 라이브러리를 이름만으로 구분**하기 위함이다.
`apps/` 아래 것은 프로세스로 뜬다. `modules/` 아래 것은 절대 뜨지 않는다.

## 모듈 의존성

```
  ┌──────────────────────┐        ┌───────────────────────┐
  │  apps/api-market     │        │  apps/api-trading     │
  └──────────┬───────────┘        └───────────┬───────────┘
             └──────────────┬─────────────────┘
                            ▼
                 ┌──────────────────────┐
                 │    modules/core      │  ← 내부 의존성 없음 (leaf)
                 └──────────────────────┘

  ingest (Go)  ──  JVM 트리와 빌드 의존성 0
                   런타임 결합만: Redis 키 포맷 + DB 스키마
```

2계층이라 순환 참조가 구조적으로 불가능하다.

### 규칙

1. **`core`는 어떤 내부 모듈도 의존하지 않는다.** leaf를 유지해야 순환 의존이 생기지 않는다.
2. **공유 모듈은 `core` 하나다.** Redis 접근은 `core` 안의 `cache` 패키지다. 두 앱이 모두 `core`와 Redis를 쓰므로 별도 모듈로 빼도 디커플링 이득이 없다. Redis만 필요하고 `core`는 필요 없는 소비자가 생기면 그때 분리한다.
3. **앱 ↔ 앱 의존은 금지.** 두 앱이 공유할 것이 생기면 `core`로 올린다.
4. **앱은 쓰는 모듈을 명시적으로 선언한다.** transitive 의존에 기대지 않는다.
5. **`ingest`는 Gradle 빌드에 포함하지 않는다.**
6. **앱 클래스는 `com.ssafy.a107` 에 둔다.** 모듈 패키지(`.core`)의 부모이므로 기본 컴포넌트 스캔만으로 전부 잡힌다. 앱을 하위 패키지로 내리면 `scanBasePackages` · `@EntityScan` · `@EnableJpaRepositories` 를 앱마다 중복 선언해야 한다.

### Gradle 선언

`core`에는 `org.springframework.boot` 플러그인을 **적용하지 않는다.** BOM만 가져오므로 `bootJar`를 비활성화하는 우회 설정이 필요 없고, 실제로 일반 jar만 생성된다.

| 모듈 | 플러그인 | 내부 의존 |
|---|---|---|
| `modules/core` | `io.spring.dependency-management` | 없음 (leaf) |
| `apps/api-market` | `org.springframework.boot` + `io.spring.dependency-management` | `core` |
| `apps/api-trading` | `org.springframework.boot` + `io.spring.dependency-management` | `core` |

`core`는 엔티티·리포지토리를 공개 API로 노출하므로 JPA 스타터를 **`api`** 로 선언한다.
반면 Redis 스타터는 `cache` 패키지 내부에서만 쓰므로 **`implementation`** 이다 — 앱이 `RedisTemplate`을 직접 컴파일 참조할 수 없다.

## 명령

```bash
./gradlew projects                      # 모듈 트리 확인
./gradlew build                         # 전체 빌드
./gradlew :apps:api-market:bootRun      # :8080
./gradlew :apps:api-trading:bootRun     # :8081

# 의존성 규칙 검증
./gradlew :modules:core:dependencies  --configuration compileClasspath | grep "project :"   # 출력 없어야 정상 (leaf)
./gradlew :apps:api-market:dependencies --configuration compileClasspath | grep "project :" # :modules:core 만
```
