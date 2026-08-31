# ingest

> Upbit 데이터 수집 · 적재 파이프라인. Go 모듈 하나에서 바이너리 3개를 만든다.
> 실행 파이프라인은 아직 구현 전이며, 공통 도메인과 오류 처리 기반만 있는 상태다.

JVM 트리와 **빌드 의존성이 전혀 없다.** Gradle은 이 디렉토리의 존재를 모른다 (Gradle 프로젝트는 형제 디렉토리 `../api` 에 있음). 결합은 런타임에만 존재하며, 그 접점은 두 개다 — Redis 키/값 포맷과 DB 스키마.

## 바이너리

| 바이너리 | 역할 | 실행 형태 |
|---|---|---|
| `cmd/producer` | Upbit WebSocket 구독 → Kafka 발행 | 상시 실행 |
| `cmd/consumer` | Kafka 소비 → Redis 진행 봉 갱신 + PostgreSQL 확정 분봉 적재 | 상시 실행 |
| `cmd/backfill` | Upbit REST 조회 → Kafka 누락 이벤트 발행 | 주기 실행 · 일회성 |

세 바이너리를 **하나의 Go 모듈**에 둔 이유는 Upbit 클라이언트, Kafka 메시지 구조체, DB 모델을 전부 공유하기 때문이다. 모듈을 쪼개면 이 코드를 중복 구현해야 한다.

## 구조

```
ingest/
├─ cmd/                  각 디렉토리가 package main → 바이너리 1개
│  ├─ producer/main.go     설정 로드 → 의존성 조립 → 실행 → 그레이스풀 종료
│  ├─ consumer/main.go
│  └─ backfill/main.go
├─ internal/             모듈 외부에서 import 불가 (Go 툴체인이 강제)
│  ├─ common/domain/        공통 생성·수정 시각
│  ├─ common/exception/     오류 코드·원인 보존·재시도 정책
│  ├─ candle/domain/        확정 MinuteCandle
│  ├─ upbit/               WebSocket · REST 클라이언트, 재연결, 페이로드 파싱
│  ├─ kafka/               토픽 상수, 메시지 구조체, 프로듀서 · 컨슈머 래퍼
│  ├─ redis/               캐시 키 규칙, 쓰기          ← Go↔JVM 계약의 원본
│  ├─ store/               PostgreSQL 확정 분봉 UPSERT ← DB 스키마 계약
│  └─ config/              환경변수 로드
└─ go.mod
```

**`cmd/`가 왜 필요한가** — Go는 디렉토리 1개 = 패키지 1개이고, 실행 파일은 `package main`인 디렉토리에서 하나만 나온다. 바이너리 3개를 만들려면 `package main` 디렉토리가 물리적으로 3개 필요하다. `cmd/<이름>/`은 그 3개를 어디 둘지에 대한 Go 관례이며, 디렉토리 이름이 그대로 바이너리 이름이 된다.

**`internal/`이 왜 필요한가** — 관례가 아니라 **Go 툴체인이 강제하는 규칙**이다. `internal/` 아래 패키지는 그 부모(`ingest/`) 밖에서 import하면 컴파일 에러가 난다. 모듈 내부 구현이 외부로 새는 것을 문서가 아니라 컴파일러가 막는다.

`cmd/`는 얇은 껍데기(각 50~100줄)로 유지하고 실제 로직은 전부 `internal/`에 둔다.

## 담지 않는 것

| 담지 않음 | 이유 / 대신 어디로 |
|---|---|
| **DB 스키마 생성 · 변경** | 스키마는 `backend/api` 의 Flyway 마이그레이션이 단독 소유한다. 여기서는 UPSERT만 |
| **Redis 읽기** (JVM 소비용) | 읽기는 Spring API 조회 계층에서 담당한다. 여기서는 쓰기만 |
| **Raw 캔들 DB 적재** | 원본·진행 중 이벤트는 Kafka에 두고 PostgreSQL에는 확정 분봉만 저장 |
| REST API 서빙 | `backend/apps/*` |
| 랭킹 계산 · 전략 로직 | `backend/apps/*` |

## 의존성

- **내부**: 없음 — JVM 모듈과 빌드 의존성 0
- **외부**: `twmb/franz-go`, `redis/go-redis/v9`, `jackc/pgx/v5`, `gorilla/websocket`

## 규칙

1. **Redis 키 포맷을 바꾸면 Spring API의 Redis 읽기 계약을 같은 커밋에서 함께 수정한다.** 컴파일러가 잡아주지 못하는 계약이다.
2. **DB 스키마를 변경하지 않는다.** 컬럼이 필요하면 `backend/api/src/main/resources/db/migration` 에 SQL 을 추가하고, 그 후 여기의 UPSERT를 맞춘다.
3. **키 규칙과 토픽 이름은 상수로 한 곳에만 정의한다.** (`internal/redis`, `internal/kafka`)
4. **모든 바이너리는 SIGINT / SIGTERM에서 그레이스풀 종료한다.** 상시 실행 프로세스가 중간 상태를 남기면 재기동 시 중복·누락이 생긴다.

## 명령

```bash
go build ./...            # 3개 바이너리 컴파일 확인
go vet ./...
go run ./cmd/producer
go run ./cmd/consumer
go run ./cmd/backfill
```
