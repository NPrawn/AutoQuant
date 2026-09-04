# Backtest Job Contract

`S15P21A107-64`의 백테스트 실행 생성과 Worker 연동을 위한 메시지 계약이다.
현재 Backend의 `strategy_id` 타입과 ERD를 기준으로 `Long`을 사용한다.

## Message

- 메시지 형식: JSON
- 계약 버전: `backtest.job.v1`
- 발행 시점: `backtest_run`이 `QUEUED`로 저장된 이후
- 식별자: `jobId`는 메시지 재처리 식별자, `backtestId`는 실행 이력 식별자

```json
{
  "jobId": "2fd8cc72-a39b-4fc5-8b4c-cf83ea734dc3",
  "backtestId": "e4b4e9e0-8d17-4a47-98d4-4d9f06d97b2b",
  "strategyId": 1,
  "marketDataVersion": "market-v1",
  "engineVersion": "engine-v1"
}
```

## Fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `jobId` | UUID | Yes | Job 재처리와 추적에 사용하는 식별자 |
| `backtestId` | UUID | Yes | `trading.backtest_run`의 실행 식별자 |
| `strategyId` | Long | Yes | 실행할 불변 전략 스냅샷의 식별자 |
| `marketDataVersion` | String | Yes | Worker가 읽어야 하는 시장 데이터 버전 |
| `engineVersion` | String | Yes | 실행 엔진 버전 |

## Processing Rules

1. Worker는 `backtestId`로 실행 설정을 PostgreSQL에서 다시 조회한다.
2. 메시지의 버전 필드와 실행 행의 버전 필드가 다르면 실행하지 않고 실패 처리한다.
3. Worker는 `QUEUED` 실행만 `RUNNING`으로 전이한다.
4. 성공적으로 결과를 저장하고 `COMPLETED`로 전이한 뒤 메시지를 ACK한다.
5. 처리 중 장애로 ACK하지 못한 메시지는 다른 Worker가 재처리할 수 있어야 한다.
6. 동일 `jobId` 재처리 시 결과 중복을 막는 멱등성 정책은 RabbitMQ Worker 작업(`S15P21A107-70`)에서 구현한다.

RabbitMQ exchange, queue, routing key와 재시도·Dead Letter 정책은 Worker 작업에서 확정한다.
