# 운영 서비스 검증

`develop` 배포 후 Jenkins는 `infra/verify-production-services.sh`를 실행한다.

## 판정 기준

- Frontend는 `200`, Backend API는 `200` 또는 인증 미포함 상태의 `401`을 반환한다.
- PostgreSQL, Redis, Kafka, RabbitMQ, AI, Frontend와 Ingest Producer·Consumer·Backfill 컨테이너의 Docker health 상태가 `healthy`다.
- API, Ingest Producer·Consumer·Backfill, Backtest Worker를 포함한 필수 컨테이너가 실행 중이다.
- Kafka topic 초기화 컨테이너가 성공적으로 종료됐다.

## 수동 실행

배포 저장소 경로에서 다음 명령으로 Jenkins와 같은 점검을 실행한다.

```bash
sudo bash infra/verify-production-services.sh \
  infra/docker-compose.prod.yml \
  /etc/a107/.env.production
```

Ingest healthcheck는 PostgreSQL과 Redis 연결을 확인하고, Kafka를 사용하는 Producer·Consumer에서는 브로커 Ping도 확인한다. 의존 서비스가 재시작된 뒤에는 위 스크립트를 다시 실행해 Backend·Ingest가 정상 상태로 복구됐는지 확인한다.

## 의존 서비스 복구 리허설

스테이징 환경 또는 승인된 점검 시간에만 아래 절차를 수행한다. 운영 환경에서는 서비스 영향 범위를 확인하고 점검 공지를 완료한 뒤 실행한다.

```bash
sudo docker compose --env-file /etc/a107/.env.production \
  -f infra/docker-compose.prod.yml restart redis

sudo bash infra/verify-production-services.sh \
  infra/docker-compose.prod.yml \
  /etc/a107/.env.production
```

Redis 검증이 끝나면 같은 방식으로 `postgres`, `kafka`, `rabbitmq`를 각각 재시작하고 검증 스크립트가 통과하는지 확인한다. 각 단계에서 실패하면 해당 컨테이너 로그를 수집하고 다음 의존 서비스 재시작은 중단한다.
