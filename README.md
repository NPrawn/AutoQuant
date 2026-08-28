# S15P21A107 — 암호화폐 모의투자 · 전략 플랫폼

Upbit 실시간 시세를 수집·적재하고, 랭킹과 전략 수립을 제공하며, 모의투자와 백테스팅을 실행하는 폴리글랏 서비스.

## 아키텍처

```
Upbit ──WebSocket──> Producer ──> Kafka ──> Consumer ──┬──> Redis ──┐
 (Go)                  (Go)                  (Go)      └──> DB ────┤
                                                                    │
Upbit ──REST────────> Backfill ─────────────────────────────> DB ───┤
                       (Go)      1분봉 누락 보정                     │
                                                                    ↓
              [AI] FastAPI ──────────>  Backend API  ──────────> React
                (Python)                 (Java 21)            (TradingChart)
                                    랭킹·체결·전략수립
                                    모의투자·백테스팅
```

**설계의 핵심**: 수집 파이프라인(Go)과 API 서버(Java)는 코드를 공유하지 않는다.
두 언어는 오직 두 지점에서만 만난다 — **Redis 키/값 포맷**과 **DB 스키마**. 둘 다 Go가 쓰고 Java가 읽는다.

## 폴더 구성

| 경로 | 언어 | 역할 |
|---|---|---|
|  `backend/api/` | Java 21 | Spring Boot API 서버 (모듈러 모놀리스) |
| `backend/ingest/` | Go | Upbit 수집·적재 파이프라인 (바이너리 3개) |
| `ai/` | Python | FastAPI 전략 추론 서비스 |
| `frontend/` | TypeScript | 트레이딩 차트 UI (React) |
| `infra/` | — | 로컬 개발용 Kafka · Redis · PostgreSQL |

각 디렉토리의 `README.md`에 책임 범위와 경계가 정의되어 있다.

API 서버는 **앱 하나에 패키지로 경계를 둔** 구조다 (`domain` · `cache` · `auth` · `market` · `trading`). 배포 단위를 쪼갤 근거가 관측되면 그때 분리한다 — 이유는 `backend/README.md` 참조.

## 기술 스택

| 영역 | 스택 |
|---|---|
| API 서버 | Java 21 (LTS), Spring Boot 3.5.16, Gradle |
| 수집 파이프라인 | Go |
| AI | Python, FastAPI |
| 프론트엔드 | React 19, TypeScript, Vite 8 |
| 저장소 | PostgreSQL 16, Redis |
| 메시징 | Kafka (KRaft 모드) |

## 로컬 실행

```bash
# 1. 인프라 기동 (Kafka, Redis, PostgreSQL)
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml up -d

# 2. API 서버 (부팅 시 Flyway 마이그레이션 적용)
cd backend/api && ./gradlew bootRun          # :8080

# 3. 프론트엔드
cd frontend && npm install && npm run dev  # :5173

# 4. 수집 파이프라인
cd backend/ingest
go run ./cmd/producer                    # Upbit → Kafka
go run ./cmd/consumer                    # Kafka → Redis + DB
go run ./cmd/backfill                    # 1분봉 누락 보정 (일회성)
```

> API 서버를 먼저 한 번 띄워 Flyway 마이그레이션을 적용해야 한다. Go 쪽은 스키마를 생성하지 않고 INSERT만 한다.

## 개발 환경 요구사항

| 도구 | 버전 |
|---|---|
| JDK | 21 (Temurin 권장) |
| Go | 1.26+ |
| Python | 3.12+ |
| Node.js | 20+ |
| Docker | Compose v2 포함 |
