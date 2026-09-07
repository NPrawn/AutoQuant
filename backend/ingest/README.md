# ingest

> Upbit 데이터 수집 · 적재 파이프라인. Go 모듈 하나에서 바이너리 3개를 만든다.
> 현재 Producer는 Kafka Consumer Group에서 Collector 샤드를 할당받고, DB 수집 대상 중 담당 종목만 Upbit WebSocket으로 구독한다. 수신한 체결은 Kafka `tick-raw`와 Redis Pub/Sub에 서로 독립적으로 발행한다.
> Consumer는 `tick-raw`를 파티션별로 집계하고 진행 봉을 Redis에 체크포인트로 저장한다. 확정 봉은 PostgreSQL에 UPSERT하며, 필수 저장과 Kafka offset 커밋이 성공한 뒤 최신 진행 봉을 Redis Pub/Sub으로 발행한다.

JVM 트리와 **빌드 의존성이 전혀 없다.** Gradle은 이 디렉토리의 존재를 모른다 (Gradle 프로젝트는 형제 디렉토리 `../api` 에 있음). 결합은 런타임에만 존재하며, 그 접점은 두 개다 — Redis 키/값 포맷과 DB 스키마.

## 바이너리

| 바이너리 | 역할 | 실행 형태 |
|---|---|---|
| `cmd/producer` | DB 수집 대상 감지 → Upbit WebSocket 체결 수신 | 상시 실행 |
| `cmd/consumer` | Kafka 체결 소비 → Redis 진행 봉 → PostgreSQL 확정 봉 → offset 커밋 | 상시 실행 |
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
│  ├─ asset/domain/         시장 데이터 수집 대상
│  ├─ candle/domain/        확정 MinuteCandle
│  ├─ trade/domain/         실시간 MarketTrade
│  ├─ producer/             수집 대상 변경 감지 · Collector 샤드 소유권
│  ├─ consumer/             체결 메시지 검증 · partition별 1분봉 집계
│  ├─ upbit/               WebSocket · REST 클라이언트, 재연결, 페이로드 파싱
│  ├─ kafka/               토픽 상수, 메시지 구조체, Consumer Group · 프로듀서 · 컨슈머 래퍼
│  ├─ redis/               진행 봉 체크포인트 저장·복구, 실시간 채널 발행 ← Go↔JVM 계약의 원본
│  ├─ store/               PostgreSQL 수집 대상 조회·확정 분봉 UPSERT
│  └─ config/              환경변수 로드
├─ tests/                기능별 외부 패키지 테스트와 testdata
└─ go.mod
```

**`cmd/`가 왜 필요한가** — Go는 디렉토리 1개 = 패키지 1개이고, 실행 파일은 `package main`인 디렉토리에서 하나만 나온다. 바이너리 3개를 만들려면 `package main` 디렉토리가 물리적으로 3개 필요하다. `cmd/<이름>/`은 그 3개를 어디 둘지에 대한 Go 관례이며, 디렉토리 이름이 그대로 바이너리 이름이 된다.

**`internal/`이 왜 필요한가** — 관례가 아니라 **Go 툴체인이 강제하는 규칙**이다. `internal/` 아래 패키지는 그 부모(`ingest/`) 밖에서 import하면 컴파일 에러가 난다. 모듈 내부 구현이 외부로 새는 것을 문서가 아니라 컴파일러가 막는다.

`cmd/`는 얇은 껍데기(각 50~100줄)로 유지하고 실제 로직은 전부 `internal/`에 둔다.

## 담지 않는 것

| 담지 않음 | 이유 / 대신 어디로 |
|---|---|
| **DB 스키마 생성 · 변경** | 스키마는 `backend/api` 의 Flyway 마이그레이션이 단독 소유한다. 여기서는 UPSERT만 |
| **Redis 조회 API 제공** | 사용자 조회는 Spring API가 담당한다. Go는 집계 복구를 위해 진행 봉 체크포인트만 읽는다. |
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
5. **확정 분봉은 `reference.asset.asset_id` UUID를 문자열로 전달한다.** 거래소 마켓 코드를 DB 내부 식별자로 직접 사용하지 않는다.

## 명령

Producer 실행에는 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`, `REDIS_PORT`가 필요하다. Kafka 브로커가 여러 개면 쉼표로 구분한다. Producer는 `collector-shards` 토픽과 `upbit-trade-collectors` 그룹으로 담당 종목을 결정하고, 수신한 체결을 마켓 코드 key로 `tick-raw`에 전송하는 동시에 `trade:realtime:updates` 채널에도 JSON으로 발행한다. Redis 발행은 크기 2,048의 비동기 큐와 전용 worker가 담당하므로 Redis 응답이 Kafka 전송을 지연시키지 않는다. 큐가 가득 차면 가장 오래된 실시간 알림을 제거하고 최신 체결을 유지한다. `UPBIT_WEBSOCKET_URL`과 `COLLECTION_TARGET_REFRESH_INTERVAL`은 선택 설정이며 기본값은 각각 Upbit 공식 WebSocket 주소와 `5s`다.

Consumer 실행에는 동일한 DB·Kafka·Redis 설정이 필요하다. Consumer는 Poll 배치에서 변경된 종목별 마지막 진행 봉만 모아 `candle:1m:progress:{partition}` Hash에 한 번의 `HSET`으로 저장한 뒤 Kafka offset을 커밋한다. 각 필드에는 진행 봉, 마지막 체결 순번, Kafka source offset을 함께 저장한다. 커밋 성공 후 종목별 최신 진행 봉 한 건을 `candle:1m:live:updates` 채널에 JSON으로 발행하며 내부 복구 값인 `source_offset`은 제외한다. Pub/Sub 실패는 저장·커밋 결과를 되돌리지 않는다. 최근 1,000개 확정 봉 캐시는 아직 구현하지 않는다.

```bash
go build ./...            # 3개 바이너리 컴파일 확인
go vet ./...
go run ./cmd/producer
go run ./cmd/consumer
go run ./cmd/backfill
```
