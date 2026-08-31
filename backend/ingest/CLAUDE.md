# backend/ingest — Go pipeline

Go 1.26 기반 Upbit 수집·적재 파이프라인이다. 이 디렉토리의 작업에는 루트 `CLAUDE.md`와 아래 규칙을 적용한다.

## 데이터 흐름

```text
Upbit WebSocket/REST → Producer → Kafka → Consumer
                                          ├→ Redis: 진행 중 봉
                                          └→ PostgreSQL: 확정 분봉
```

- 원본·진행 중 이벤트를 PostgreSQL Raw 테이블에 저장하지 않는다.
- Kafka 원본 이벤트의 수명은 retention과 디스크 정책을 따른다.
- PostgreSQL에는 확정된 `MinuteCandle`만 멱등 UPSERT한다.
- 멱등 키는 `(asset_id, interval_minutes, open_time)`이다.
- Kafka offset은 PostgreSQL·Redis 처리가 성공한 뒤 수동 커밋한다.
- 동일 마켓 이벤트 순서를 위해 Kafka 메시지 키는 마켓 코드를 사용한다.

## 패키지

```text
internal/
├── common/domain       BaseEntity
├── common/exception    ErrorCode, Error, 재시도 정책
├── asset/domain        시장 데이터 수집 대상
├── candle/domain       MinuteCandle
├── trade/domain        실시간 MarketTrade
├── producer            수집 대상 변경 감지
├── upbit               외부 API와 응답 DTO
├── kafka               토픽·메시지·Producer/Consumer 래퍼
├── redis               최신 상태 쓰기
├── store               PostgreSQL 조회·INSERT·UPSERT
└── config              환경변수 설정
```

일반 Go 컨벤션은 `common` 패키지를 금지하지만, 이 프로젝트의 `internal/common/domain`과 `internal/common/exception`은 Java 구조와의 일관성을 위해 팀이 명시적으로 허용했다. 다른 범용 `util`, `helper`, `common` 패키지를 추가로 만들지는 않는다.

## 도메인과 저장 모델

- 패키지명은 `market`이 아니라 `candle`이다.
- `MinuteCandle`은 확정 분봉만 표현한다.
- 내부 `asset_id` UUID는 문자열, 분봉 간격은 `int16`, 시각은 `time.Time`을 사용한다.
- decimal 라이브러리가 확정되기 전까지 가격·거래량 문자열을 그대로 유지한다.
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
