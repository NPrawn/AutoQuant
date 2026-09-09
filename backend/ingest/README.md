# ingest

> Upbit 데이터 수집 · 적재 파이프라인. Go 모듈 하나에서 바이너리 3개를 만든다.
> 현재 Producer는 Kafka Consumer Group에서 Collector 샤드를 할당받고, DB 수집 대상 중 담당 종목만 Upbit WebSocket으로 구독한다. 수신한 체결은 Kafka `tick-raw`와 Redis Pub/Sub에 서로 독립적으로 발행한다.
> Consumer는 `tick-raw`를 파티션별로 집계하고 진행 봉을 Redis에 체크포인트로 저장한다. 다음 체결이 없는 진행 봉도 마감 유예 후 확정하며, 확정 봉은 PostgreSQL과 최근 Redis 캐시에 저장한다.
> Backfill은 Upbit REST를 기준으로 미검증 1분봉 구간을 PostgreSQL과 비교해 누락·불일치 봉을 보정하고 최근 Redis 캐시도 갱신한다.

JVM 트리와 **빌드 의존성이 전혀 없다.** Gradle은 이 디렉토리의 존재를 모른다 (Gradle 프로젝트는 형제 디렉토리 `../api` 에 있음). 결합은 런타임에만 존재하며, 그 접점은 두 개다 — Redis 키/값 포맷과 DB 스키마.

## 바이너리

| 바이너리 | 역할 | 실행 형태 |
|---|---|---|
| `cmd/producer` | DB 수집 대상 감지 → Upbit WebSocket 체결 수신 | 상시 실행 |
| `cmd/consumer` | Kafka 체결 소비 → Redis 진행 봉 → PostgreSQL 확정 봉 → offset 커밋 | 상시 실행 |
| `cmd/backfill` | Upbit REST 조회 → 과거 적재·확정 분봉 검증/보정 | 상시 실행 · 기본 5분 주기 |

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
│  ├─ candlesync/           미검증 시간 범위 계산 · 과거 적재 · 최신 봉 검증
│  ├─ candlerollup/         1분봉 기반 공통 상위 봉 집계 · 재처리 · 진행 봉 합성
│  ├─ upbit/               WebSocket · REST 클라이언트, 재연결, 페이로드 파싱
│  ├─ kafka/               토픽 상수, 메시지 구조체, Consumer Group · 프로듀서 · 컨슈머 래퍼
│  ├─ redis/               진행 봉 체크포인트, 최근 확정 봉 캐시, 실시간 채널 ← Go↔JVM 계약의 원본
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

### 다중 간격 봉

- 지원 간격은 **1·2·5·15·60·240·1440분**이다. 모든 종목에 같은 알고리즘을 사용하며 UTC 경계로 나눈다. 1440분봉은 UTC 00:00(KST 09:00)에 시작한다.
- `candle`과 Kafka 체크포인트는 기존 1분봉 기준을 유지한다. `candlerollup`은 저장된 1분봉을 기준으로 시가=첫 봉, 종가=마지막 봉, 고가/저가=최대/최소, 거래량/대금=합계로 계산한다. 무거래 분은 만들지 않는다.
- Flyway **V14**는 `ops.candle_rollup_job`과 트리거를 추가한다. Consumer 저장 및 Backfill 보정 트랜잭션 안에서 영향받는 종목·간격·시작 시간의 작업을 함께 기록한다. 기존 1분봉 이력도 작업으로 등록하므로 데이터가 많으면 마이그레이션·초기 집계 시간이 필요하다.
- `cmd/consumer`를 올리면 공통 Rollup 작업도 자동 시작한다. 별도 바이너리나 간격별 스케줄러는 없다. 5초마다 최대 100개(회당 20초 제한), 최신 구간부터 처리하며 PostgreSQL advisory lock으로 인스턴스 간 실행을 조정한다.
- 종료 시각+5초가 지난 작업을 DB에 UPSERT하고 Redis까지 갱신한 뒤 완료한다. DB/Redis 실패 시 작업이 남아 재시작 후 재처리된다. 처리 중 원본이 바뀌면 revision이 달라지므로 작업을 삭제하지 않는다.
- **종료 시각이 지났다는 것은 원본 검증 완료를 뜻하지 않는다.** 과거 이력 수집 중이거나 지연 데이터가 있으면 부분 결과일 수 있다. 기존 `candlesync`가 1분봉을 보정하면 종료된 상위 봉도 다시 집계한다.
- 최근 확정 봉은 `candle:{intervalMinutes}m:recent:{assetId}` ZSet에 **종목·간격별 최신 1,000개**를 유지한다. Lua로 같은 score 교체와 trim을 원자 실행한다. 캐시 miss는 Spring이 해당 간격의 저장된 DB 봉으로 보충한다. 진행 봉은 이 ZSet에 섞지 않는다.
- 진행 봉은 커밋된 1분봉 체크포인트에 현재 구간의 확정 1분봉 합계를 더해 `candle:{intervalMinutes}m:live:updates`로 발행한다. 같은 분의 반복 갱신은 교체하며 중복 합산하지 않는다. 종목당 분이 바뀔 때 UTC 하루 이내(최대 1,440개)의 원본을 한 번 읽고 합계를 재사용한다. 재기동 시 다시 읽으며 유휴 상태는 정리한다.
- 진행 중 구간의 REST 보정은 다음 분 갱신에서 합계에 반영된다. Pub/Sub은 best-effort이며 무체결 시장에 별도 진행 알림은 만들지 않는다. 종료된 봉은 작업이 저장하고 프론트의 1분 주기 REST 재조회로 반영한다.
- 1분봉의 기존 REST·Redis 키·채널 계약은 유지한다. 충분한 과거 이력이 없으면 일봉도 1,000개 미만이며 인위적으로 채우지 않는다.

### 실행

Producer 실행에는 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`, `REDIS_PORT`가 필요하다. Kafka 브로커가 여러 개면 쉼표로 구분한다. Producer는 `collector-shards` 토픽과 `upbit-trade-collectors` 그룹으로 담당 종목을 결정하고, 수신한 체결을 마켓 코드 key로 `tick-raw`에 전송하는 동시에 `trade:realtime:updates` 채널에도 JSON으로 발행한다. Redis 발행은 크기 2,048의 비동기 큐와 전용 worker가 담당하므로 Redis 응답이 Kafka 전송을 지연시키지 않는다. 큐가 가득 차면 가장 오래된 실시간 알림을 제거하고 최신 체결을 유지한다. `UPBIT_WEBSOCKET_URL`과 `COLLECTION_TARGET_REFRESH_INTERVAL`은 선택 설정이며 기본값은 각각 Upbit 공식 WebSocket 주소와 `5s`다.

Consumer 실행에는 동일한 DB·Kafka·Redis 설정이 필요하다. Consumer는 Poll 배치에서 변경된 종목별 마지막 진행 봉만 모아 `candle:1m:progress:{partition}` Hash에 한 번의 `HSET`으로 저장한 뒤 Kafka offset을 커밋한다. 각 필드에는 진행 봉, 마지막 체결 순번, Kafka source offset을 함께 저장한다. 커밋 성공 후 확정 봉을 `candle:1m:recent:{assetId}` Sorted Set에 저장하고 score가 같은 기존 봉을 교체한 뒤 최신 1,000개만 유지한다. 이어서 종목별 최신 진행 봉 한 건을 `candle:1m:live:updates` 채널에 JSON으로 발행한다.

Kafka Poll은 최대 1초마다 반환되어 파티션별 진행 봉의 만료를 검사한다. 분 종료 후 5초가 지난 봉은 다음 체결이 없어도 PostgreSQL에 UPSERT한 뒤 Redis 진행 Hash에서 제거하고 최근 1,000개 캐시에 반영한다. 이 확정은 이미 커밋된 체결 상태를 닫는 작업이므로 Kafka offset을 추가로 커밋하지 않는다. 리밸런싱으로 새 파티션을 받으면 Redis 체크포인트를 먼저 복구하고 이미 만료된 봉을 즉시 확정한다. 체결이 전혀 없었던 분의 빈 봉은 생성하지 않는다. 최근 캐시와 Pub/Sub 실패는 필수 저장·커밋 결과를 되돌리지 않으며, 초기 캐시 miss는 향후 Spring 조회 API가 PostgreSQL에서 복구한다.

Backfill 실행에는 DB·Redis 설정과 `CANDLE_HISTORY_FROM`이 필요하다. 신규 종목은 현재 시각의 직전 1분을 제외한 시점을 빈 검증 구간으로 시작하고, 최신 미검증 구간을 먼저 처리한 뒤 과거 방향으로 최대 200분씩 확장한다. 종목별 검증 완료 범위 `[verified_from, verified_to)`는 `ops.minute_candle_sync_state`에 저장하므로 완료 구간은 다시 조회하지 않는다. 과거 수집은 `CANDLE_HISTORY_FROM`과 종목 거래지원 시작 시각 중 늦은 시점에서 멈춘다. 기본 과거 요청 예산은 실행당 240회다.

Upbit 봉과 같은 `open_time`의 PostgreSQL 봉은 REST 값으로 교체하고 `verified_at`을 기록한다. Redis Sorted Set도 같은 score의 값을 교체한 뒤 최신 1,000개만 남긴다. DB에만 존재하는 봉은 자동 삭제하지 않고 해당 구간의 검증 상태를 이동하지 않는다. Redis 갱신 실패는 정본 저장을 되돌리지 않는다. 동시에 실행된 Backfill은 PostgreSQL advisory lock으로 한 개만 처리된다. 바이너리는 시작 직후 한 번 처리하고 `CANDLE_SYNC_INTERVAL`마다 반복하며 기본값은 5분이다.

REST 봉의 소속 구간과 캐시 score는 `candle_date_time_utc`로 결정한다. Upbit `timestamp`는 마지막 틱이 **저장된 시각**이므로 분 종료를 넘을 수 있으며 `last_event_time`에 자르거나 이동하지 않고 보존한다. 실시간 집계의 같은 필드는 체결 발생 시각이다. 상위 확정 봉은 원본 시각의 최댓값을, live candle은 현재 체결 시각을 유지한다. timestamp 때문에 실패한 구간은 수정 배포 후 기존 검증 경계부터 재처리하므로 DB·Redis·검증 상태를 초기화하지 않는다. [Upbit 필드 정의](https://docs.upbit.com/kr/v1.5.9/reference/분minute-캔들-1)

```bash
go build ./...            # 3개 바이너리 컴파일 확인
go vet ./...
go run ./cmd/producer
go run ./cmd/consumer
CANDLE_HISTORY_FROM=2025-01-01T00:00:00Z go run ./cmd/backfill
```
