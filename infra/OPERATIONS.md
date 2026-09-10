# 운영 서비스 검증

`develop` 배포 후 Jenkins는 `infra/verify-production-services.sh`를 실행한다.

## 판정 기준

- Frontend는 `200`, Backend API는 `200` 또는 인증 미포함 상태의 `401`을 반환한다.
- PostgreSQL, Redis, Kafka, RabbitMQ, AI, Frontend 컨테이너의 Docker health 상태가 `healthy`다.
- API, Ingest Producer·Consumer·Backfill, Backtest Worker를 포함한 필수 컨테이너가 실행 중이다.
- Kafka topic 초기화 컨테이너가 성공적으로 종료됐다.

## 수동 실행

배포 저장소 경로에서 다음 명령으로 Jenkins와 같은 점검을 실행한다.

```bash
sudo bash infra/verify-production-services.sh \
  infra/docker-compose.prod.yml \
  /etc/a107/.env.production
```

Ingest 실행 파일은 HTTP health endpoint를 제공하지 않으므로 컨테이너 실행 상태로 확인한다. 의존 서비스가 재시작된 뒤에는 위 스크립트를 다시 실행해 Backend·Ingest가 정상 상태로 복구됐는지 확인한다.
