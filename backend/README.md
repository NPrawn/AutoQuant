# backend

서버 사이드 전체. **빌드 도구별로 디렉토리를 나눈다.**

| 디렉토리 | 언어 · 빌드 도구 | 역할 |
|---|---|---|
| [`api/`](api) | Java 21 · Gradle | Spring Boot API 서버 (모듈러 모놀리스) |
| [`ingest/`](ingest) | Go · go modules | Upbit 수집·적재 파이프라인 (바이너리 3개) |

`backend/` 자체에는 빌드 파일이 없다. **각 하위 디렉토리가 자기 빌드를 완전히 소유하므로 서로의 존재를 몰라도 된다.**

```
backend/
├─ api/
│  ├─ build.gradle · settings.gradle · gradlew
│  └─ src/main/java/com/ssafy/a107/{domain,cache,auth,market,trading}
└─ ingest/
   ├─ go.mod
   ├─ cmd/{producer,consumer,backfill}
   └─ internal/{upbit,kafka,redis,store,config}
```

## 왜 API 서버가 앱 하나인가

랭킹 API와 모의투자 API를 별도 Spring Boot 앱으로 쪼갰다가 되돌렸다. 두 쪽이 **DB 스키마 · 도메인 모델 · 인증을 전부 공유**하므로 애초에 독립 서비스가 아니었고, 쪼갠 대가만 남았다:

- Flyway를 한쪽 앱에서만 켜야 하는 비대칭 (둘 다 켜면 부팅 시 경합)
- 프론트가 CORS를 두 번 겪고 dev 프록시가 두 개
- "두 앱이 공유하는 모듈에 뭘 넣나"라는 답 없는 논쟁 — 그 모듈이 잡동사니 버킷이 된다

**배포 단위를 쪼갤 근거가 관측되면 그때 쪼갠다.** 근거란 예를 들어 백테스팅이 CPU를 오래 점유해 조회 API를 굶기는 현상이다. `api/README.md`의 패키지 경계를 지켜두면 그때 `trading`을 별도 프로세스로 떼내는 비용이 작다.

## Go↔JVM 계약

`api/`와 `ingest/`는 빌드 의존성이 0이다. 결합은 런타임에만 있고 접점은 두 개뿐이다.

| 계약 | 쓰는 쪽 | 읽는 쪽 |
|---|---|---|
| Redis 키/값 포맷 | `ingest/internal/redis` | `api` 의 `cache` 패키지 |
| DB 스키마 | `ingest/internal/store` (INSERT만) | `api` 의 `domain` 패키지 |

**컴파일러가 잡아주지 못하는 계약이다.** 한쪽만 바꾸면 런타임에 조용히 깨지므로 항상 같은 커밋에서 양쪽을 함께 수정한다. 스키마 변경은 `api/src/main/resources/db/migration`에 SQL을 추가한 뒤 Go 쪽 쿼리를 맞춘다.

Kafka는 `ingest/` 안에만 존재한다. Producer와 Consumer가 둘 다 Go라서 Kafka 메시지 구조체는 언어 경계를 넘지 않으며, JVM 쪽에 대응 클래스가 없다.

## 명령

```bash
cd api    && ./gradlew build && ./gradlew bootRun    # :8080
cd ingest && go build ./... && go vet ./...
```
