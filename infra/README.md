# infra

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

단일 브로커 개발 환경이라 Kafka 복제 계수는 모두 1이다. 토픽 자동 생성이 켜져 있다.

접속 정보는 `.env.example` 참조. `.env` 는 커밋되지 않는다.
