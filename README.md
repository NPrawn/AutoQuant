# AutoQuant

**암호화폐 투자 전략 백테스트·모의투자 플랫폼**

사용자가 필터·랭킹 조건과 투자 대상 수를 조합해 전략을 구성하고, 과거 시장 데이터로 백테스트하거나 실제 자금 없이 모의투자를 실행하는 웹 서비스입니다. Upbit 시세 수집, 전략 관리, 백테스트, 모의투자, 성과 조회를 하나의 흐름으로 연결했습니다.

| 구분 | 내용 |
| --- | --- |
| 개발 기간 | 2026.08 ~ 2026.09 |
| 팀 구성 | 5인 팀 프로젝트 |
| 담당 | 팀장 · 백엔드 · 백테스트/모의투자 실행 및 운영 |
| 개인 기여도 | 약 30% (프로젝트 전체 기준 추정) |
| 주요 기술 | Java 21, Spring Boot, PostgreSQL, RabbitMQ, Kafka, Redis, Go, Python/FastAPI, React, Docker, Jenkins |

> 이 문서는 **팀 전체 시스템의 구조**와 **개인이 직접 담당한 백엔드 구현·문제 해결**을 구분해 설명합니다. 코드와 설계의 상세 내용은 각 모듈 문서에서 확인할 수 있습니다.

## 핵심 기능

| 기능 | 설명 |
| --- | --- |
| 전략 구성 | 필터 → 랭킹 → 상위 N개 선정 → 동일 비중 배분으로 전략 구성 |
| AI 전략 변환 | 자연어 입력을 검증 가능한 전략 DSL로 변환 |
| 백테스트 | 기간·초기 자본·수수료·슬리피지 등을 설정하고 과거 시세로 가상 체결 |
| 백테스트 결과 | 수익률, 자산 곡선, 거래 내역, 종목 선정 기록 및 위험 지표 조회 |
| 모의투자 | 시장 데이터와 전략 재선정 결과를 바탕으로 가상 거래·잔고·포지션 기록 |
| 시세·차트 | Upbit 체결 데이터 수집, 확정 분봉 저장, 최근 시세·진행 봉 제공 |

## 담당 역할과 주요 성과

백엔드 개발자로서 **요청과 장시간 계산의 분리**, **메시지 전달과 데이터 정합성**, **실행 성능 및 운영 안정성**에 집중했습니다.

| 담당 영역 | 직접 수행한 작업 |
| --- | --- |
| 백테스트 실행 | 요청 생성·조회, 실행 상태 관리, RabbitMQ 작업 발행 및 독립 Worker 연동 |
| 실행 신뢰성 | Transactional Outbox, 발행 실패 재처리, DB 잠금과 실행 상태 전이 검증 |
| 결과 저장·DB | 백테스트 결과 저장 흐름, 후보군 스냅샷 JDBC Batch, DB 제약조건·인덱스 설계 |
| 성능 검증 | 24건 동시 요청 계측, Worker 수·DB 연결 풀·prefetch 조정, 시계열 재사용 및 저장 비용 개선 |
| 모의투자 | 확정 캔들 기준 재선정 흐름과 일일 재선정 누락 복구 로직 |
| 배포·운영 | Jenkins/Docker Compose 운영, 의존 서비스 준비 상태·배포 후 검증 및 장애 대응 |

**대표 성과 — 24건 동시 백테스트 전체 완료 시간 약 44.9% 감소**

- 초기 측정: **66.526초** (1회)
- 개선 후: **36.645초** (6회 측정 중앙값)
- 운영 환경: **EC2 4 vCPU, 약 15 GiB 메모리**, API·DB·시세 수집기·Worker 등이 자원을 공유하는 구성
- 주의: **동일한 조건의 단일 변경 A/B 실험은 아닙니다.** Worker 구성, 시계열 재사용, 저장 방식 등 여러 변경이 반영된 결과로, 특정 최적화 하나의 성능 향상 폭을 의미하지 않습니다.

## 시스템 아키텍처

~~~mermaid
flowchart TB
    U["Upbit 시세"] -->|"WebSocket"| P["Go Producer"]
    P -->|"체결 이벤트"| K[["Kafka"]]
    K --> C["Go Consumer"]
    U -->|"REST"| B["Go Backfill"]
    C --> PG[("PostgreSQL")]
    C --> R[("Redis")]
    B --> PG
    B --> R

    F["React Frontend"] <-->|"REST / WebSocket"| API["Spring Boot API"]
    API <--> PG
    API <--> R
    API -->|"전략 변환 요청"| AI["Python / FastAPI"]
    API -->|"Outbox 기록 · 커밋 후 발행"| MQ[["RabbitMQ"]]
    MQ --> W["Backtest Worker"]
    W <--> PG
~~~

**Kafka와 RabbitMQ의 역할을 분리**했습니다.

- **Kafka:** Go Producer가 수집한 실시간 체결 이벤트를 Consumer에 전달하는 시세 데이터 파이프라인
- **RabbitMQ:** Spring Boot API가 접수한 백테스트 계산 작업을 별도 Worker에 전달하는 작업 큐
- **PostgreSQL:** 확정 분봉, 전략, 실행 이력, 체결 원장, 자산 기록 등 영속 데이터
- **Redis:** 최근 확정 봉 캐시와 실시간 시세 이벤트 전달
- **FastAPI:** 자연어 투자 전략을 전략 DSL로 변환하는 별도 AI 서비스

Spring Boot API는 관련 도메인을 하나의 애플리케이션에서 관리하는 **모듈러 모놀리스** 구조입니다. 반면 Go 시세 수집기는 독립 모듈로 빌드하며 DB 스키마와 Redis 데이터 계약으로 연동합니다.

자세한 내용: [백엔드 구조](backend/README.md) · [API 설계](backend/api/README.md) · [시세 수집 파이프라인](backend/ingest/README.md) · [인프라](infra/README.md)

## 기술적 문제 해결

### 1. 장시간 백테스트 계산과 API 요청 분리

**문제:** 여러 종목의 과거 캔들에 대해 전략 지표 계산·리밸런싱·가상 체결·결과 저장을 반복하는 작업을 HTTP 요청에서 직접 수행하면 응답 지연과 타임아웃, 다른 API와의 자원 경쟁이 발생할 수 있습니다.

**설계·구현**

1. API는 백테스트 실행 기록과 Outbox를 저장하고 **HTTP 202 Accepted**로 요청을 접수합니다.
2. 트랜잭션 커밋 후 RabbitMQ에 작업을 발행합니다.
3. 독립 Worker는 작업을 소비해 계산하고 결과를 저장합니다.
4. 사용자는 별도 API로 **QUEUED → RUNNING → COMPLETED / FAILED** 상태와 결과를 조회합니다.

이를 통해 API 요청 수명과 계산 수명을 분리하고, Worker 수를 조절하여 부하를 관리할 수 있도록 했습니다.

구현: [BacktestRunCommandService](backend/api/src/main/java/com/ssafy/a107/trading/backtest/service/BacktestRunCommandService.java) · [BacktestJobListener](backend/api/src/main/java/com/ssafy/a107/trading/backtest/messaging/BacktestJobListener.java) · [백테스트 메시지 계약](docs/backtest-job-contract.md)

### 2. 메시지 발행 실패와 중복 실행 대응

**문제:** DB에 백테스트 요청을 저장한 직후 메시지 발행에 실패하면, 실행 이력은 존재하지만 Worker가 작업을 받지 못할 수 있습니다. 반대로 메시지가 다시 전달되면 같은 계산이 중복 실행될 위험이 있습니다.

**설계·구현**

- **Transactional Outbox:** 실행 기록과 발행 대기 Outbox를 동일 DB 트랜잭션에 기록
- **AFTER_COMMIT 발행:** 커밋된 작업만 RabbitMQ로 전달
- **재발행 경로:** 미발행 Outbox를 스케줄러가 다시 조회하고 발행 시도
- **중복 시작 방어:** Worker가 DB 행을 잠그고 실행 상태 및 메시지 버전을 검증한 뒤 상태 전이
- **장애 처리:** 실패 상태 저장, 재전달 여부 및 Dead Letter 경로 고려

**정확히 한 번 전달(exactly-once delivery)을 보장한다고 표현하지 않습니다.** 재전달이 가능하다는 전제에서 DB 상태와 실행 경계를 검사하는 구조입니다.

구현: [OutboxService](backend/api/src/main/java/com/ssafy/a107/trading/backtest/messaging/BacktestJobOutboxService.java) · [OutboxScheduler](backend/api/src/main/java/com/ssafy/a107/trading/backtest/messaging/BacktestJobOutboxScheduler.java) · [BacktestJobStartService](backend/api/src/main/java/com/ssafy/a107/trading/backtest/service/BacktestJobStartService.java) · [Flyway V16](backend/api/src/main/resources/db/migration/V16__create_backtest_job_outbox.sql)

### 3. 성능 병목 계측 및 결과 저장 최적화

**문제:** 동시 백테스트 요청의 완료 시간이 길었지만, Worker 부족과 계산·DB 저장 비용을 구분하지 않고서는 효과적인 개선 방법을 선택하기 어려웠습니다.

**접근**

- 실행 기록의 \`queued_at\`, \`started_at\`, \`completed_at\`으로 **큐 대기 시간과 실제 실행 시간**을 분리해 분석
- Worker 1·2·4개 구성을 비교해 처리량뿐 아니라 CPU·DB I/O 경합 및 처리 시간 편차 확인
- Worker의 DB 연결 풀 제한과 RabbitMQ \`prefetch=1\`을 적용해 자원 사용과 메시지 선점 조절
- 동일 캔들 윈도우의 시계열 표현을 재사용해 반복 변환을 줄이는 방향으로 엔진 개선
- 후보군 스냅샷을 **500건 단위 JDBC Batch**로 저장하고, NULL 순위·빈 목록 등의 경계 조건 검증
- 배포 후 Worker 수가 달라지지 않도록 Jenkins의 기본 Worker 복제 수 명시

**판단:** Worker 수를 단순히 최대로 늘리는 대신, 공유 EC2 환경에서 처리 시간의 일관성과 API·DB의 자원 여유를 고려해 **Worker 2개를 운영 기본값**으로 설정했습니다.

구현: [백테스트 엔진](backend/api/src/main/java/com/ssafy/a107/trading/backtest/engine/DefaultBacktestEngine.java) · [JDBC Batch Repository](backend/api/src/main/java/com/ssafy/a107/trading/backtest/repository/BacktestUniverseAssetBatchRepository.java) · [Worker 설정](backend/api/src/main/resources/application.yml) · [Jenkinsfile](Jenkinsfile)

### 4. 모의투자 일일 재선정 누락 복구

**문제:** 일일 전략 재선정 시점에 필요한 확정 캔들이 아직 준비되지 않으면, 해당 시점의 리밸런싱이 누락될 수 있었습니다.

**설계·구현**

- 실행 중인 일일 모의투자 중 재선정 복구 대상 조회
- 데이터 준비 상태를 확인하고 미완료 작업의 재선정 재시도
- 대기 상태를 로깅하여 데이터 미도착과 실행 실패를 구분

구현: [SimulationDailyRebalanceRecoveryScheduler](backend/api/src/main/java/com/ssafy/a107/trading/simulation/service/SimulationDailyRebalanceRecoveryScheduler.java) · [관련 테스트](backend/api/src/test/java/com/ssafy/a107/trading/simulation/service/SimulationDailyRebalanceRecoverySchedulerTest.java)

## 데이터 정합성과 배포 운영

- **DB 스키마 단일 소유:** Flyway SQL 마이그레이션을 기준으로 관리하며 Hibernate는 \`ddl-auto=validate\` 사용
- **데이터 제약:** 모의투자 잔고 CHECK, 실행·시점·종목 기준 중복 방지용 복합 키, Outbox 미발행 조회 인덱스 적용
- **실행 비용 반영:** 수수료, 슬리피지, 가격 호가 단위 및 수량 정밀도 등을 고려한 가상 체결
- **CI/CD:** Jenkins에서 Java·Go·Python 테스트와 프론트엔드 검증을 수행하도록 구성하고, \`develop\` 브랜치의 Docker 이미지 빌드·배포 경로 정의
- **운영 점검:** Kafka 준비 상태, 컨테이너 Health Check, 배포 후 서비스 응답 확인, 이전 이미지로 복구하는 수동 롤백 절차 마련

관련 근거: [Flyway V17](backend/api/src/main/resources/db/migration/V17__create_simulation_tables.sql) · [운영 점검](infra/OPERATIONS.md) · [롤백 절차](docs/infra-rollback.md)

## 기술 스택 및 디렉터리

| 영역 | 기술 | 위치 |
| --- | --- | --- |
| API·백테스트 | Java 21, Spring Boot, Spring Data JPA, JDBC, Flyway | [\`backend/api\`](backend/api) |
| 시세 수집·가공 | Go, Upbit WebSocket/REST, Kafka | [\`backend/ingest\`](backend/ingest) |
| AI 전략 변환 | Python, FastAPI | [\`ai\`](ai) |
| 프론트엔드 | React, TypeScript, Vite | [\`frontend\`](frontend) |
| 데이터·메시징 | PostgreSQL, Redis, Kafka, RabbitMQ | [\`infra\`](infra) |
| 빌드·배포 | Docker Compose, Jenkins, Nginx | [\`infra\`](infra) · [\`Jenkinsfile\`](Jenkinsfile) |

## 로컬 실행

### 요구 환경

- JDK 21, Go 1.26+, Python 3.12+, Node.js 20+
- Docker 및 Docker Compose v2
- 인증 및 AI 서비스 연동에 필요한 외부 환경변수/자격증명

### 1. 의존 서비스 실행

저장소 루트에서 실행합니다.

~~~bash
cp infra/.env.example infra/.env
# infra/.env에 로컬 설정을 입력합니다.
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
~~~

PostgreSQL, Redis, Kafka, RabbitMQ가 기동됩니다.

### 2. Spring Boot API 실행

서버 실행 전에 \`SSAFY_CLIENT_ID\`, \`SSAFY_CLIENT_SECRET\`, \`SSAFY_REDIRECT_URI\`, \`JWT_SECRET\` 등 필수 설정을 **실행 환경변수로** 주입해야 합니다. 민감정보는 커밋하지 않습니다.

~~~bash
cd backend/api
./gradlew bootRun
# 기본 포트: 8080
~~~

**API를 먼저 기동하여 Flyway 마이그레이션을 적용**한 뒤 시세 수집기를 실행해야 합니다. Go 수집기는 테이블을 생성하지 않고 기존 DB 스키마에 데이터를 기록합니다.

### 3. 프론트엔드·AI·시세 수집기 실행

각 명령은 저장소 루트에서 개별 터미널로 실행합니다.

~~~bash
# 프론트엔드 (:5173)
cd frontend
npm ci
npm run dev
~~~

~~~bash
# AI 서비스 (:8000)
cd ai
python -m pip install -r requirements.txt
cp .env.example .env
# 필수 AI 설정을 .env에 입력합니다.
uvicorn app.main:app --reload
~~~

~~~bash
# 시세 수집기: 서로 다른 터미널에서 실행
cd backend/ingest
go run ./cmd/producer
go run ./cmd/consumer
go run ./cmd/backfill
~~~

백테스트 계산에는 별도 \`backtest-worker\` 프로필의 Spring Boot 프로세스와 RabbitMQ가 필요합니다. 세부 환경변수, 실행 흐름 및 운영 구성이 필요한 경우 [API 문서](backend/api/README.md), [수집기 문서](backend/ingest/README.md), [AI 문서](ai/README.md), [인프라 문서](infra/README.md)를 참고하세요.

> 로컬 실행은 SSAFY 인증 및 AI 게이트웨이 자격증명과 외부 시세 API에 의존합니다. 저장소를 복제하는 것만으로 모든 서비스 기능이 동작하는 것은 아닙니다.

## 테스트와 기술 문서

| 대상 | 명령/자료 |
| --- | --- |
| 백엔드 테스트 | \`cd backend/api && ./gradlew test\` |
| Go 테스트·정적 검사 | \`cd backend/ingest && go test ./... && go vet ./...\` |
| 프론트엔드 정적 검증·빌드 | \`cd frontend && npm ci && npm run lint && npm run build\` |
| Python 테스트 | \`cd ai && python -m pytest -q\` |
| 백테스트 메시지 계약 | [docs/backtest-job-contract.md](docs/backtest-job-contract.md) |
| 백테스트·모의투자 데이터 모델 | [docs/backtest_simulation_erd.md](docs/backtest_simulation_erd.md) |
| 백엔드 기능별 상세 설명 | [backend/api/README.md](backend/api/README.md) |
| 배포 검증 | [infra/OPERATIONS.md](infra/OPERATIONS.md) |

위 명령은 저장소에 정의된 실행 방법을 안내하는 것이며, **이 README 개편 작업에서 테스트를 새로 실행하거나 통과를 확인한 것은 아닙니다.**

---

이 프로젝트는 **5인 팀의 결과물**입니다. 시세 수집, AI, 프론트엔드 등 전체 구성은 팀의 구현 영역이며, 본문에서 명시한 백테스트 실행·정합성·모의투자 복구·성능 검증·운영 영역을 중심으로 개인 담당 내용을 정리했습니다.
