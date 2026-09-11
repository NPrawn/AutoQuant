# infra

운영 상태 점검: [운영 서비스 검증](OPERATIONS.md)

> 로컬 개발용 Kafka · Redis · PostgreSQL.

## 명령

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml up -d      # 기동
docker compose -f infra/docker-compose.yml ps         # 상태
docker compose -f infra/docker-compose.yml down       # 정지
docker compose -f infra/docker-compose.yml down -v    # 볼륨까지 삭제
```

## 서비스

| 서비스 | 포트 | 비고 |
|---|---|---|
| PostgreSQL 16 | 5432 | 스키마는 backend 의 Flyway 가 생성한다 |
| Redis 7 | 6379 | Go consumer 가 쓰고 backend 의 `cache` 패키지가 읽는다 |
| Kafka | 9092 | KRaft 모드 — ZooKeeper 불필요 |
| RabbitMQ | 5672, 15672 | AMQP Broker, Management UI |

`kafka-init`이 Collector 소유권 조정용 `collector-shards`와 원본 체결용 `tick-raw` 토픽을 각각 파티션 6개로 생성하고 검증한 뒤 종료한다. 단일 브로커 개발 환경이라 Kafka 복제 계수는 모두 1이다. 두 토픽은 자동 생성에 의존하지 않는다.

접속 정보는 `.env.example` 참조. `.env` 는 커밋되지 않는다.

## 운영 분봉 동기화

운영 Nginx는 `/api/` 아래 모든 요청의 접두사를 떼서 Spring에 전달하고, `/ws`는 JWT 쿠키와 Host/Origin을 유지한 WebSocket Upgrade로 전달한다. 종목·봉 API 도 같은 규칙을 따르므로 경로별 예외가 없다. `nginx/j15a107.p.ssafy.io.conf` 수정은 운영 배포 시 별도로 적용한다.

Spring은 `server.forward-headers-strategy: native`로 프록시가 전달한 HTTPS를 인식한다. 운영 API 포트는 기존처럼 `127.0.0.1:18080` 바인딩을 유지한다. `/ws`의 전달 헤더는 Nginx가 덮어쓰며 Origin은 변경하지 않는다.

운영 Compose를 기동하면 `ingest-backfill`도 별도 상시 컨테이너로 함께 시작된다. 시작 직후 한 번 동기화하고 `CANDLE_SYNC_INTERVAL` 간격으로 반복하며 기본값은 `5m`이다. 호스트의 cron이나 systemd 등록은 필요하지 않다.

Backfill은 한 컨테이너 안에서 작업을 순차 실행하고, 여러 서버에서 컨테이너가 함께 기동되더라도 PostgreSQL advisory lock이 한 실행만 허용한다.
