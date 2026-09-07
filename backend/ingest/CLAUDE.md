# backend/ingest — Go pipeline

Go 1.26 기반 Upbit 수집·적재 파이프라인이다. 이 디렉토리의 작업에는 루트 `CLAUDE.md`와 아래 규칙을 적용한다.

## 데이터 흐름

```text
Upbit WebSocket/REST → Producer → Kafka → Consumer
                       └→ Redis Pub/Sub: 실시간 체결
                                          ├→ Redis: 진행 중 봉
                                          ├→ Redis: 최근 확정 분봉 1,000개
                                          ├→ Redis Pub/Sub: 커밋된 진행 봉
                                          └→ PostgreSQL: 확정 분봉

Upbit REST → Candle Sync → PostgreSQL: 과거 적재·확정 봉 검증
                          └→ Redis: 보정된 최근 확정 분봉
```

- 원본·진행 중 이벤트를 PostgreSQL Raw 테이블에 저장하지 않는다.
- Kafka 원본 이벤트의 수명은 retention과 디스크 정책을 따른다.
- PostgreSQL에는 확정된 `MinuteCandle`만 멱등 UPSERT한다.
- 멱등 키는 `(asset_id, interval_minutes, open_time)`이다.
- Kafka offset은 PostgreSQL·Redis 처리가 성공한 뒤 수동 커밋한다.
- 진행 봉은 분 종료 5초 후 주기적으로 확정한다. Sweep은 새 Kafka 레코드를 처리하지 않으므로 offset을 추가 커밋하지 않는다.
- 진행 봉 Pub/Sub은 offset 커밋 뒤에 실행하며, 실패해도 이미 완료된 저장·커밋을 실패로 바꾸지 않는다.
- 체결로 확정된 분봉 캐시는 offset 커밋 뒤에, Sweep으로 확정된 분봉 캐시는 PostgreSQL 저장과 진행 봉 삭제 뒤에 갱신한다. 캐시 실패는 원본 저장과 커밋 결과를 되돌리지 않는다.
- 실시간 체결 Pub/Sub은 bounded 비동기 큐와 전용 worker로 Kafka 발행 경로와 분리한다. 큐가 가득 차면 가장 오래된 알림을 제거하고 최신 체결을 유지한다.
- 동일 마켓 이벤트 순서를 위해 Kafka 메시지 키는 마켓 코드를 사용한다.
- Candle Sync는 Upbit REST를 정합성 기준으로 미검증 시간 범위만 조회하며 Kafka를 거치지 않는다.
- 검증 범위는 `ops.minute_candle_sync_state`에 연속 구간으로 저장하고, REST로 검증된 봉은 지연 Consumer가 다시 덮어쓰지 않는다.

## 패키지

```text
internal/
├── common/domain       BaseEntity
├── common/exception    ErrorCode, Error, 재시도 정책
├── asset/domain        시장 데이터 수집 대상
├── candle              체결을 1분봉으로 집계
├── candle/domain       MinuteCandle
├── trade/domain        실시간 MarketTrade
├── producer            수집 대상 변경 감지·Collector 샤드 소유권
├── consumer            Kafka 체결 검증·partition별 1분봉 집계 오케스트레이션
├── candlesync          Upbit 1분봉 과거 적재·주기 검증 오케스트레이션
├── upbit               외부 API와 응답 DTO
├── kafka               토픽·메시지·Consumer Group·Producer/Consumer 래퍼
├── redis               진행 봉 체크포인트, 최근 확정 봉 Sorted Set, Pub/Sub
├── store               PostgreSQL 조회·INSERT·UPSERT
└── config              환경변수 설정
```

Consumer는 파티션별로 순차 집계하고, 서로 다른 파티션만 병렬 처리한다. 한 Poll에서 같은 종목이 여러 번 변경되어도 마지막 진행 봉만 모아 파티션별 한 번의 Redis `HSET`으로 저장한다. PostgreSQL 확정 봉과 Redis 진행 봉 저장이 모두 성공한 Poll 배치만 offset을 커밋한다. Kafka Poll은 최대 1초마다 반환되어 진행 봉을 Sweep한다. 만료된 봉은 PostgreSQL UPSERT 성공 후 Redis 진행 Hash에서 제거한다. 리밸런싱으로 회수된 파티션의 인메모리 상태는 폐기하고 재할당 시 Redis에서 다시 복구하며, 복구한 만료 봉은 즉시 확정한다. 체결이 없었던 분의 빈 봉은 만들지 않는다.

Redis Pub/Sub 채널은 `internal/redis`에서만 정의한다. `trade:realtime:updates`는 Kafka 적재 결과와 무관하게 수신한 체결을 건별 발행하고, `candle:1m:live:updates`는 저장 및 offset 커밋에 성공한 Poll의 종목별 최신 진행 봉만 발행한다. Pub/Sub은 복구 저장소가 아니므로 구독자가 놓친 메시지를 재처리하는 용도로 사용하지 않는다.

최근 확정 분봉은 `candle:1m:recent:{assetId}` Sorted Set에 저장한다. score는 `open_time`의 Unix millisecond이며 동일 score는 교체하고 최신 1,000개만 유지한다. 이 데이터는 파생 캐시이므로 초기 miss와 유실은 PostgreSQL에서 복구한다.

일반 Go 컨벤션은 `common` 패키지를 금지하지만, 이 프로젝트의 `internal/common/domain`과 `internal/common/exception`은 Java 구조와의 일관성을 위해 팀이 명시적으로 허용했다. 다른 범용 `util`, `helper`, `common` 패키지를 추가로 만들지는 않는다.

## 도메인과 저장 모델

- 패키지명은 `market`이 아니라 `candle`이다.
- `MinuteCandle`은 확정 분봉만 표현한다.
- 내부 `asset_id` UUID는 문자열, 분봉 간격은 `int16`, 시각은 `time.Time`을 사용한다.
- 가격·거래량·거래대금은 `shopspring/decimal`로 다루고, Kafka 메시지와 Redis 값 같은 계약 경계에서는 문자열을 유지한다.
- Upbit DTO, Kafka 메시지, Candle 도메인, DB 저장 파라미터를 하나의 구조체로 재사용하지 않고 경계에서 변환한다.
- Go에서 DDL을 실행하지 않는다. DB 변경은 `backend/api` Flyway에 추가한다.

## 오류 처리

- `panic`은 `main`의 복구 불가능한 초기화 실패 외에는 사용하지 않는다.
- `internal/common/exception.New`와 `Wrap`으로 안정적인 코드를 부여한다.
- 원인 에러를 `%w` 또는 `Wrap`으로 보존한다.
- 일시적인 외부 장애만 재시도하고 잘못된 데이터와 DB 제약 위반은 스킵 또는 별도 처리한다.
- 에러를 반환하는 계층에서는 로그를 남기지 않는다. 최종 처리 경계에서 `log/slog`로 코드, 마켓, 토픽, 파티션, 오프셋을 기록한다.
- 무한 재시도하지 않는다. 제한 횟수를 초과하면 원인과 식별자를 남기고 메시지 처리 정책에 따라 커밋·격리한다.

## 컨벤션

아래 문서를 함께 따른다.

- `../docs/convention/go-convention.md`
- `../docs/convention/go-test-convention.md`

## 검증

```bash
gofmt -w <변경한 .go 파일>
go test -race ./...
go vet ./...
go build ./...
```

Go 도구가 없는 환경이면 임의로 설치하지 말고 미실행 항목을 완료 보고에 명시한다.
