# 백엔드 확장 작업·검증 기록

이 문서는 [백엔드 확장계획](../AutoQuant_백엔드_확장계획.md)의 작업 결과와 검증 증빙을 누적한다. 추가 작업의 주된 목적은 포트폴리오에 설명할 수 있는 문제 해결 경험과 증빙을 확보하는 것이다. 작업 범위와 진행 체크리스트는 [가상 이슈 목록](backend-extension-issues.md), 커밋·푸시·기록 절차는 [Git 컨벤션 5절](git-convention.md#5-백엔드-확장-작업의-가상-이슈-운영)을 따른다.

## 기록 원칙

- 가상 이슈의 최소 커밋 단위가 끝나면 검증 후 로컬 커밋하고, 이슈 전체가 완료되면 작업 커밋을 푸시한 뒤 이슈 단위의 완료 기록을 작성한다. 기록도 문서 커밋으로 추가 푸시한다.
- 코드에서 확인한 사실, 사용자가 실행해 보고한 결과, 실제 측정 결과, 아직 검증하지 않은 가설을 구분한다.
- 기존 구현을 이번 확장 작업에서 새로 구현한 성과로 쓰지 않는다. 개인이 직접 수행한 범위도 함께 기록한다.
- 작업 상태는 `예정`, `진행 중`, `완료`, `보류`로 표시한다. 준비 완료와 기능 검증 완료를 구분한다.
- 성능 수치는 버전, 환경, 데이터, 부하, 반복 횟수와 함께 기록한다. 빌드 시간, API 응답 지연, 백테스트 완료시간은 서로 다른 지표다.
- 원본 보고서, 로그, 실행계획, 그래프, 코드·테스트·CI 링크를 가능한 한 결과와 함께 보존한다. `build/` 등 생성물은 직접 수정하거나 커밋하지 않고, 필요한 수치와 조건을 이 문서에 요약한다.
- 원본 로그와 화면을 보관하거나 공개할 때 비밀값과 개인정보를 제외한다.
- 실패하거나 개선되지 않은 실험도 원인과 선택한 대응을 기록한다. 미검증 결과는 포트폴리오 문장에 완료 사실로 쓰지 않는다.
- 각 안내에 이슈 ID, 커밋 단위, 확인할 결과와 남길 증빙을 표시한다. 진행 중에는 이슈 체크리스트와 원본 증빙을 유지하고 이 문서의 세부 완료 기록은 작업 커밋 푸시 후에 작성한다.

아래 날짜별 세부 기록은 운영 방식 전환 전의 이력으로 보존한다. 가상 이슈가 완료·푸시됐다는 뜻은 아니며, 현재 테스트 복원 작업은 `AQ-BE-001`로 인계한다. 이슈 완료 기록은 별도의 이슈 ID 절로 추가한다.

## 현재 진행 상태

| 작업 | 상태 | 확인된 결과 | 남은 확인 |
|---|---|---|---|
| 로컬 백엔드 빌드·테스트 기준선 확인 | 완료 | 빌드 성공, 테스트 912개 중 통과 895개·건너뜀 17개·실패/오류 0개 | 전체 P0 완료에는 CI·백테스트 기준선 재현도 필요 |
| 건너뛴 테스트의 조건 확인 | 완료 | DB·Redis 환경변수에 따라 5개 클래스에서 17개 항목 건너뜀 | 파라미터 테스트는 실제 실행 시 사례별로 확장됨 |
| 테스트 전용 Redis·PostgreSQL 준비 | 완료 | 준비 상태와 테스트 접속 확인. Flyway V37까지 적용 및 Redis 관련 테스트 통과 | 테스트 데이터와 구성 문제 수정 |
| 캔들 테스트의 초기 데이터 충돌 수정 | 완료 | 테스트 전용 종목 코드로 분리한 뒤 해당 클래스 6개 모두 통과 | 전체 테스트와 함께 실행한 결과는 별도 확인 필요 |
| 차트 테스트의 서비스 Bean 등록 수정 | 완료 | 사용자 import·`@Import` 변경을 소스에서 확인 | 테스트 데이터 준비 후 실제 컨텍스트 로딩·조회 검증 |
| 대상 5개 클래스의 통합 테스트 검증 | 진행 중 | 첫 실행 43개 중 통과 38개·실패 5개. 이후 캔들 클래스는 별도 실행에서 6개 통과 | 차트 종목 테스트 수정 후 전체 43개 재검증 |
| CI에서 외부 의존성 테스트 실행 확인·보강 | 예정 | 로컬 결과와 별도 검증 필요 | 실행 환경, 건너뜀 여부, 실패 시 배포 차단 결과 |
| 관측성·장애 복구·DB 최적화·부하 실험 | 예정 | 확장계획의 후속 과제 | 구현 및 실측 증빙 |

## 2026-11-12 — 로컬 테스트 기준선과 통합 테스트 환경 준비

### 목적과 문제

추가 개선을 시작하기 전에 현재 코드가 빌드되는지, 실제로 어떤 테스트까지 검증되는지 확인한다. 빌드 성공만으로 PostgreSQL·Redis 연동 검증이 완료됐다고 판단하지 않도록 건너뛴 테스트를 확인한다.

### 수행 범위

- 사용자: 로컬 빌드 실행, 테스트 보고서 확인, Docker 실행 상태 확인, 테스트용 컨테이너 생성·이름 수정·준비 상태 확인.
- Codex: 저장소 코드·설정 확인, 실행 명령 안내, JUnit XML 결과 집계, 작업 기록 작성.
- 이 단계에서는 애플리케이션 로직, 테스트 코드, CI 설정을 변경하지 않았다.

### 환경과 증빙 출처

| 항목 | 기록 | 출처·한계 |
|---|---|---|
| 실험일 | 2026-11-12 | 빌드 로그와 JUnit XML 타임스탬프 |
| 코드 버전 | 기록 시점 HEAD `2409bbb8915c` | 빌드 당시 커밋은 별도 수집하지 않아 동일 여부 미확인 |
| 실행 OS·아키텍처 | macOS / arm64 | 사용자가 공유한 Docker Client 출력 |
| Gradle | 9.7.1 | 사용자가 공유한 빌드 출력 |
| Java | 프로젝트 toolchain 21 | 실제 JVM 버전 출력은 미수집 |
| Docker | Client/Engine 28.5.1, Docker Desktop 4.48.0 | 사용자가 공유한 `docker version` 출력 |
| 호스트 CPU·RAM | 미수집 | 성능 비교 전에 수집 필요 |
| Redis 구성 | 안내한 이미지 `redis:7-alpine`, 컨테이너 `autoquant-test-redis`, 호스트 `127.0.0.1:16379` | 사용자 `PONG` 보고. 실제 이미지 ID/digest는 미수집 |
| PostgreSQL 구성 | 안내한 이미지 `postgres:16-alpine`, 컨테이너 `autoquant-test-postgres`, 호스트 `127.0.0.1:15432`, DB·사용자 `candle_test` | 사용자 이름 수정 및 `accepting connections` 보고. 실제 이미지 ID/digest는 미수집 |
| 부하·데이터 조건 | 기본 빌드·테스트 1회 | 성능 부하 실험 및 운영 데이터셋 검증은 수행하지 않음 |

### 1. 기본 빌드 실행

실행 위치: `backend/api`.

```bash
bash ./gradlew build --rerun-tasks
```

사용자가 공유한 결과:

```text
BUILD SUCCESSFUL in 1m 32s
9 actionable tasks: 9 executed
```

처음 `./gradlew`로 실행했을 때는 `permission denied`가 발생했다. Bash로 스크립트를 실행해 해결했다. 실행 권한을 수정한 것으로 기록하지 않는다.

`@MockBean` 제거 예정 경고와 Gradle deprecated 기능 경고가 있었으며 빌드는 성공했다. 경고 원인 정리와 수정은 아직 수행하지 않았다. 1분 32초는 이번 빌드의 실행시간이며 서비스 처리 성능 개선 수치가 아니다.

### 2. JUnit 결과 확인

사용자가 공유한 HTML 보고서 목록과 로컬 `backend/api/build/test-results/test/TEST-*.xml` 168개를 확인했다. XML 결과 시각은 2026-11-12 18:22~18:23 KST다.

| 항목 | 결과 |
|---|---:|
| 전체 테스트 | 912 |
| 통과 | 895 |
| 실패 | 0 |
| 오류 | 0 |
| 건너뜀 | 17 |

건너뛴 테스트가 포함된 클래스:

| 클래스 | 전체 | 통과 | 건너뜀 | 실행 조건 |
|---|---:|---:|---:|---|
| `RedisRecentMinuteCandleCacheIntegrationTest` | 8 | 0 | 8 | `CANDLE_TEST_REDIS_PORT` |
| `MinuteCandleRepositoryIntegrationTest` | 6 | 0 | 6 | `CANDLE_TEST_DB_PORT` |
| `ChartAssetRepositoryIntegrationTest` | 1 | 0 | 1 | `CANDLE_TEST_DB_PORT` |
| `RealtimeRedisIntegrationTest` | 1 | 0 | 1 | `REALTIME_TEST_REDIS_PORT` |
| `RealtimeWebSocketIntegrationTest` | 18 | 17 | 1 | 해당 Redis 연계 테스트에 `REALTIME_TEST_REDIS_PORT` 필요 |
| **합계** | **34** | **17** | **17** | |

조건은 각 테스트 코드의 `@EnabledIfEnvironmentVariable`에서 확인했다. 관련 코드:

- [Redis 캔들 캐시 통합 테스트](../backend/api/src/test/java/com/ssafy/a107/candle/infrastructure/redis/RedisRecentMinuteCandleCacheIntegrationTest.java)
- [캔들 Repository 통합 테스트](../backend/api/src/test/java/com/ssafy/a107/candle/repository/MinuteCandleRepositoryIntegrationTest.java)
- [차트 종목 Repository 통합 테스트](../backend/api/src/test/java/com/ssafy/a107/asset/repository/ChartAssetRepositoryIntegrationTest.java)
- [실시간 Redis 통합 테스트](../backend/api/src/test/java/com/ssafy/a107/realtime/infrastructure/redis/RealtimeRedisIntegrationTest.java)
- [WebSocket 통합 테스트](../backend/api/src/test/java/com/ssafy/a107/realtime/infrastructure/websocket/RealtimeWebSocketIntegrationTest.java)

HTML 보고서 위치는 `backend/api/build/reports/tests/test/index.html`이다. HTML과 XML 원본은 다음 테스트 실행으로 바뀔 수 있어 아래 ZIP으로 별도 보관했다. CI 아티팩트 보관은 아직 구성하지 않았다.

| 증빙 | 보관 내용 |
|---|---|
| 로컬 원본 보관 위치 | `~/AutoQuant-evidence/2026-11-12-baseline-2409bbb8915c.zip` |
| 포함 파일 | JUnit XML 168개, HTML 보고서와 관련 파일 410개, 실행 조건 요약 `record.txt` |
| 무결성 확인 | ZIP 내부 파일 검사 통과 |
| SHA-256 | `874531531d723048b2db59b0845ca2efa4a4c473205a9d42629839250801c731` |

ZIP은 저장소 밖에 보관하며 소유자만 읽고 쓸 수 있도록 파일 권한을 설정했다. 이 보관본은 로컬 증빙이며 외부 공개용으로 검토·게시한 자료는 아니다.

### 3. 테스트용 서비스 준비

기존 개발용 기본 포트와 구별되는 로컬 포트로 테스트용 서비스를 준비하도록 안내했다. 사용자가 다음 결과를 보고했다.

```text
Redis: PONG
PostgreSQL: accepting connections
```

이는 서비스 준비 상태 확인이다. 테스트의 DB 인증, Flyway 적용, Repository 쿼리와 Redis 계약 검증은 다음 실행에서 확인해야 한다.

### 통합 테스트 첫 실행 명령

아래 명령으로 첫 실행을 진행했다. 결과와 후속 작업은 다음 절에 기록한다.

실행 위치: `backend/api`. 환경변수는 이 명령에만 적용한다.

```bash
CANDLE_TEST_DB_PORT=15432 \
CANDLE_TEST_REDIS_PORT=16379 \
REALTIME_TEST_REDIS_PORT=16379 \
bash ./gradlew test --rerun-tasks \
  --tests '*RedisRecentMinuteCandleCacheIntegrationTest' \
  --tests '*MinuteCandleRepositoryIntegrationTest' \
  --tests '*ChartAssetRepositoryIntegrationTest' \
  --tests '*RealtimeRedisIntegrationTest' \
  --tests '*RealtimeWebSocketIntegrationTest'
```

완료 조건과 남길 증빙:

- 빌드 성공 여부와 대상 5개 클래스의 테스트 수·실패 수·오류 수·건너뜀 수를 확인한다. 최초에는 미실행 보고서의 34개를 예상했으나 실제 파라미터 사례를 포함하면 43개다.
- 성공하면 이 기록에 실행일, 조건, 클래스별 결과를 추가한다.
- 실패해도 테스트 이름, 원인, 대응, 재실행 결과를 추가한다.
- HTML 보고서의 집계 화면과 필요한 원본을 다음 실행으로 덮어쓰기 전에 보관한다. 생성물은 저장소 밖 또는 CI 아티팩트로 보관하고 저장 위치를 기록한다.

### 기준선 준비 시점에 설명할 수 있는 내용과 한계

작업 기록으로 사용할 수 있는 표현:

> 기존 백엔드의 로컬 빌드를 재현하고, 912개 테스트 중 17개가 환경 조건에 따라 실행되지 않음을 확인했다. PostgreSQL·Redis를 사용하는 검증을 위해 테스트 전용 컨테이너를 준비했다.

이 단계는 후속 개선을 검증하기 위한 기준선 준비이며 단독으로 성능 개선이나 장애 복구 실적을 보여 주지는 않는다. “전체 테스트 통과”, “통합 테스트 CI 자동화 완료”, “장애 복구 실증”, “서비스 성능 개선”은 아직 기록하지 않는다. 기존 24건 동시 처리의 개선율 44.9%도 이번 신규 성과로 다시 사용하지 않는다.

## 2026-11-12 — 실제 DB·Redis 통합 테스트 첫 실행과 실패 분석

### 목적과 수행 범위

환경변수 조건으로 건너뛰던 테스트를 실제 PostgreSQL·Redis에 연결해 실행하고 문제를 확인한다. 사용자가 위 명령을 실행했으며, Codex가 로컬 JUnit XML·테스트 코드·관련 서비스와 마이그레이션을 확인했다. 이 분석 단계에서는 애플리케이션 코드와 테스트 코드를 수정하거나 테스트를 추가 실행하지 않았다.

### 실행 결과

- JUnit XML 시각: 2026-11-12 18:43~18:44 KST.
- 코드 HEAD: 기록 시점 `2409bbb8915c`. Git에서 추적 중인 코드의 변경은 확인되지 않았다. 이 문서와 확장계획은 미추적 상태다.
- 환경변수: `CANDLE_TEST_DB_PORT=15432`, `CANDLE_TEST_REDIS_PORT=16379`, `REALTIME_TEST_REDIS_PORT=16379`.
- 사용자 보고서의 전체 소요시간: 46.299초. 테스트 준비와 첫 Flyway 적용을 포함한 값이며 성능 개선 지표로 쓰지 않는다.

| 클래스 | 전체 | 통과 | 실패 | 건너뜀 |
|---|---:|---:|---:|---:|
| `ChartAssetRepositoryIntegrationTest` | 1 | 0 | 1 | 0 |
| `MinuteCandleRepositoryIntegrationTest` | 6 | 2 | 4 | 0 |
| `RedisRecentMinuteCandleCacheIntegrationTest` | 17 | 17 | 0 | 0 |
| `RealtimeRedisIntegrationTest` | 1 | 1 | 0 | 0 |
| `RealtimeWebSocketIntegrationTest` | 18 | 18 | 0 | 0 |
| **합계** | **43** | **38** | **5** | **0** |

JUnit의 별도 오류(`errors`) 집계는 0개다. 테스트가 실제로 실행됐지만 DB 관련 구성·데이터 문제로 5개가 실패했다. 전체 통합 검증은 아직 완료되지 않았다.

### 테스트 수가 34개에서 43개로 달라진 이유

Redis 캐시 테스트의 `@ParameterizedTest`에는 필수 필드 10개에 대한 `@ValueSource`가 있다. 클래스가 비활성화됐을 때는 이 메서드가 한 항목으로 집계됐으나 실제 실행에서는 10개 사례로 확장됐다. 따라서 해당 클래스는 8개에서 17개로, 5개 클래스 합계는 34개에서 43개로 집계된다. 코드나 테스트를 9개 새로 추가한 결과가 아니다. 최초에 안내한 완료 기준을 43개로 정정한다.

### 확인한 실패 원인

1. **차트 종목 테스트의 서비스 Bean 누락 — 1개 실패.** `AssetReadService`는 `CollectedAssetReadService`를 생성자로 받지만, `ChartAssetRepositoryIntegrationTest.TestConfiguration`은 전자만 `@Import`한다. 가장 안쪽 원인은 `NoSuchBeanDefinitionException`이며 실제 조회 검증 전에 컨텍스트 로딩이 실패했다. 또한 현재 조회는 수집 중인 종목만 반환하므로 이후 수정에서는 수집 대상 데이터도 현재 계약에 맞춰 준비해야 한다.
2. **Flyway 초기 데이터와 캔들 테스트 데이터 충돌 — 4개 실패.** V21은 `reference.asset`에 `KRW-BTC`, `KRW-ETH` 등을 넣는다. 캔들 테스트도 같은 거래소·마켓 코드로 다른 UUID의 종목을 INSERT해 `uq_asset_exchange_market_code` 유니크 제약에 걸렸다. 테스트 결과의 `DuplicateKeyException`과 마이그레이션의 초기 데이터를 대조해 확인했다. UUID를 무작위로 만드는 것만으로 마켓 코드 중복은 피할 수 없다.

관련 코드:

- [차트 테스트의 Spring 구성](../backend/api/src/test/java/com/ssafy/a107/asset/repository/ChartAssetRepositoryIntegrationTest.java)
- [현재 차트 종목 조회 서비스](../backend/api/src/main/java/com/ssafy/a107/asset/service/AssetReadService.java)
- [수집 대상 조회 서비스](../backend/api/src/main/java/com/ssafy/a107/asset/service/CollectedAssetReadService.java)
- [캔들 테스트 데이터 생성](../backend/api/src/test/java/com/ssafy/a107/candle/repository/MinuteCandleRepositoryIntegrationTest.java)
- [V21의 종목 초기 데이터](../backend/api/src/main/resources/db/migration/V21__seed_contest_round_1.sql)

Flyway 로그에서 총 36개 마이그레이션의 V37까지 적용 성공을 확인했다. 이번 실패를 DB 접속 실패나 마이그레이션 실패로 기록하지 않는다.

### 보관한 증빙

| 항목 | 내용 |
|---|---|
| 원본 ZIP | `~/AutoQuant-evidence/2026-11-12-integration-failed-2409bbb8915c.zip` |
| 포함 파일 | JUnit XML 5개, HTML 보고서와 관련 파일 35개, 집계·실행 조건 `record.json` |
| 무결성 확인 | ZIP 내부 파일 검사 통과 |
| SHA-256 | `d72e1c036f8540f7d6f7ab33c163dafd9bd990bf5b984137afe1d9964072842d` |

저장소 밖의 로컬 보관본이며 원본 보고서를 직접 수정하지 않았다.

### 첫 실패 이후 안내한 수정과 검증 기준

먼저 `MinuteCandleRepositoryIntegrationTest`에서 사용하는 종목 코드를 초기 데이터와 겹치지 않게 분리한다. 이 파일의 `"BTC"`, `"ETH"`, `"XRP"` 인수를 각각 `"TESTBTC"`, `"TESTETH"`, `"TESTXRP"`로 변경하면 기존 helper가 테스트 전용 마켓 코드와 기준 통화를 만든다. 과거 Flyway 파일이나 기존 DB 데이터를 변경하지 않는다.

그다음 `backend/api`에서 해당 클래스만 실행한다.

```bash
CANDLE_TEST_DB_PORT=15432 \
bash ./gradlew test --rerun-tasks \
  --tests '*MinuteCandleRepositoryIntegrationTest'
```

목표는 6개 통과·실패/오류/건너뜀 0개다. 결과를 보관한 뒤 차트 종목 테스트의 Bean 구성과 수집 대상 fixture를 별도 작업으로 수정한다. 전체 43개 재검증과 CI 자동 검증은 후속 작업이다.

### 포트폴리오 기록

> 외부 의존성을 연결해 건너뛰던 통합 테스트를 실행하고, Flyway 초기 데이터와 fixture의 유니크 키 충돌 및 현재 서비스 의존성과 테스트 구성의 불일치를 발견했다. 실패 보고서를 보존하고 원인을 코드와 대조해 수정할 지점을 정리했다.

첫 실패를 분석한 시점에는 수정·재실행 전이므로 해결 완료나 안정성 향상 수치를 주장하지 않았다. 이후 캔들 테스트 수정 결과는 다음 절에 구분해 기록한다.

## 2026-11-12 — 캔들 테스트 데이터 분리와 6개 통과 확인

### 문제와 수행 내용

Flyway의 종목 초기 데이터와 테스트 fixture가 같은 마켓 코드를 사용해 캔들 테스트 6개 중 4개가 실패했다. 사용자가 `MinuteCandleRepositoryIntegrationTest` 안의 종목 인수를 `TESTBTC`, `TESTETH`, `TESTXRP`로 변경하고 해당 클래스만 다시 실행했다.

테스트 전용 종목 코드를 사용해 초기 데이터와 분리했다. 임의 UUID만 바꾸면 `(exchange, exchange_market_code)` 유니크 제약의 충돌을 피할 수 없다는 원인에 맞춘 수정이다. 과거 Flyway 파일과 애플리케이션 코드는 변경하지 않았다. Codex는 사용자 변경의 diff와 JUnit XML을 확인하고 기록·원본 보관을 수행했다.

### 검증 결과

명령은 앞 절의 `CANDLE_TEST_DB_PORT=15432`를 지정한 단일 클래스 실행과 같다. JUnit XML 시각은 2026-11-12 18:50:56 KST다.

| 항목 | 수정 전 | 수정 후 |
|---|---:|---:|
| 해당 클래스 전체 테스트 | 6 | 6 |
| 통과 | 2 | 6 |
| 실패 | 4 | 0 |
| 오류 | 0 | 0 |
| 건너뜀 | 0 | 0 |

사용자가 공유한 출력은 `BUILD SUCCESSFUL in 6s`, `5 actionable tasks: 5 executed`다. XML의 해당 클래스 실행시간은 2.604초다. 초기 전체 실행과 대상·DB 초기화 상태가 다르므로 이 시간을 성능 향상 수치로 비교하지 않는다.

코드 버전은 HEAD `2409bbb8915c`에 사용자 미커밋 테스트 변경이 더해진 상태다. 전체 43개 통합 테스트나 기본 빌드 전체를 다시 통과한 결과로 확대해 쓰지 않는다. 남은 차트 종목 테스트는 아직 수정·재검증 전이다.

### 보관한 증빙

| 항목 | 내용 |
|---|---|
| 원본 ZIP | `~/AutoQuant-evidence/2026-11-12-candle-integration-passed-2409bbb8915c.zip` |
| 포함 파일 | JUnit XML 1개, HTML 보고서와 관련 파일 11개, 수정한 테스트 소스, `source-change.patch`, 조건·결과 `record.json` |
| 무결성 확인 | ZIP 내부 파일 검사 통과 |
| SHA-256 | `32e45f0036d093c5ec2ecb2fcb5392871c1d3aaae569452e118d46f90b6ec452` |

### 포트폴리오에 사용할 수 있는 설명

> 실제 PostgreSQL 통합 테스트에서 Flyway 초기 데이터와 테스트 fixture의 유니크 키 충돌을 발견했다. 테스트 전용 종목 코드로 데이터를 분리한 뒤 해당 클래스의 실패 4개를 해소하고 6개 모두 통과함을 확인했다.

이는 테스트 구성·데이터 문제를 찾아 검증 가능하게 만든 사례다. 서비스 처리 속도나 운영 장애 복구 성과로 표현하지 않는다.

### 다음 작은 작업

`ChartAssetRepositoryIntegrationTest`에서 `CollectedAssetReadService`를 import하고 `TestConfiguration`의 `@Import`에 등록하도록 안내했다. 사용자의 반영 결과와 이어지는 fixture 작업은 다음 절에 기록한다.

## 2026-11-12 — 차트 테스트의 서비스 등록 수정과 데이터 준비

### 목적과 수행 내용

첫 통합 실행에서 `CollectedAssetReadService` Bean 누락으로 차트 종목 테스트의 컨텍스트 로딩이 실패했다. 사용자가 해당 클래스의 Java import와 `TestConfiguration`의 `@Import` 등록을 수정했다고 보고했으며, Codex가 실제 소스에서 두 변경을 확인했다.

변경은 테스트 설정에 한정된다. 서비스 등록의 코드 반영은 완료됐으나 해당 수정 후 테스트는 아직 실행하지 않았다. 따라서 Bean 누락 오류가 런타임에서 해결됐다고 확정하지 않는다.

### 다음 작은 작업 — 조회 계약에 맞는 fixture 준비

현재 `AssetReadService`는 수집 중이면서 거래지원 중인 Upbit KRW 종목만 반환한다. 다음 fixture를 준비한 뒤 해당 테스트 1개를 실행한다.

- 초기 데이터와 구별되도록 기준 통화를 `TESTETH`, `TESTBTC`, `TESTXRP`, `TESTADA`, `TESTDOGE`로 사용한다.
- 정상 원화 종목 2개, 거래 중단 종목, 거래지원 종료 종목, BTC 마켓 종목을 각각 생성하고 `MarketDataCollectionTarget`을 등록한다. 생성자의 기본 수집 상태는 `ENABLED`다.
- 정상 원화 종목 하나는 수집 대상 등록 없이 저장해 미수집 종목이 제외되는지 검증한다.
- 종목 저장 순서를 ETH→BTC로 두고, 조회 결과는 `KRW-TESTBTC`, `KRW-TESTETH` 순서인지 확인한다.
- 수집 대상 저장은 부수 효과가 드러나는 `persistCollected` helper로 표현한다. `flush`·`clear` 뒤 실제 조회를 검증한다.

실행 위치는 `backend/api`다.

```bash
CANDLE_TEST_DB_PORT=15432 \
bash ./gradlew test --rerun-tasks \
  --tests '*ChartAssetRepositoryIntegrationTest'
```

완료 기준은 1개 통과·실패/오류/건너뜀 0개다. 결과를 받은 뒤 코드 변경과 보고서 원본을 보관하고 기록한다. 전체 43개 재검증과 CI 자동 검증은 별도 후속 작업이다.

## AQ-BE-000 — 가상 이슈·커밋·푸시·기록 운영 방식 정의

- **완료일:** 2026-11-12 KST.
- **브랜치:** `feat/AQ-BE-000`.
- **기준 커밋:** `2409bbb8915c`.
- **푸시한 작업 HEAD:** `c3af0a86fc0fc9bf8354a7f589f1d59f3b55e3b5`.
- **작업 커밋:** [c3af0a8 — 백엔드 확장 작업의 가상 이슈 운영 절차 정의](https://github.com/NPrawn/AutoQuant/commit/c3af0a86fc0fc9bf8354a7f589f1d59f3b55e3b5).
- **원격 확인:** 2026-11-12 19:07 KST에 작업 커밋 푸시 성공을 확인했고, 이어 `git ls-remote`로 `origin`의 해당 브랜치가 위 HEAD를 가리킴을 확인했다.

### 목적과 변경

기존의 안내 단계별 기록 방식을 가상 이슈와 검증 가능한 커밋 단위로 바꿨다. 사용자가 이슈·커밋·푸시·기록 시점을 지정했고, Codex가 저장소 문서와 브랜치를 구성했다.

- `AQ-BE-000`~`009`로 전체 작업을 나누고, 각 이슈에 목적·커밋 단위·완료 조건을 정의했다.
- 가상 이슈별 `feat/AQ-BE-NNN` 브랜치, 검증 후 로컬 커밋, 이슈 완료 후 작업 커밋 푸시, 완료 기록의 추가 커밋·푸시 순서를 Git 규칙의 원본에 반영했다.
- 확장계획과 작업 기록의 운영 규칙을 연결하고, 방식 전환 전 기록은 당시의 이력으로 보존했다.
- 변경 중이던 두 테스트 파일은 이 문서 커밋에 포함하지 않고 작업 트리에 보존했다. 실제 GitHub·Jira 이슈나 PR은 생성하지 않았다.

### 검증과 증빙

- 4개 문서의 상대 파일 링크, 마지막 개행, 코드 블록 쌍, 줄 끝 공백을 확인했다.
- 가상 이슈 10개의 상세 카드와 커밋 단위가 존재하는지 확인했다.
- staged 파일이 운영 문서 4개뿐임을 확인했고 `git diff --cached --check`를 통과했다.
- 이슈 작업 커밋의 원격 브랜치와 SHA를 확인했다. 문서 변경이므로 이 이슈에서 애플리케이션 빌드나 테스트를 다시 실행하지 않았다.

### 포트폴리오 설명과 한계

> 백엔드 개인 확장 작업을 가상 이슈와 검증 가능한 커밋 단위로 나누고, 이슈별 작업 커밋·실험 증빙·완료 기록이 연결되도록 작업 흐름을 구성했다.

이는 이후 기술 개선을 추적하기 위한 작업 관리 결과다. 서비스 성능이나 장애 복구 성과를 새로 검증한 것은 아니다. 완료 기록은 이 절을 별도 문서 커밋으로 추가 푸시하고, 그 커밋 해시는 사용자 완료 보고에 남긴다.

### 다음 이슈

`AQ-BE-001`에서 이미 검증된 캔들 fixture 수정을 첫 커밋으로 분리하고, 차트 fixture·실행 절차·전체 검증을 이어간다. `AQ-BE-001`의 커밋은 해당 이슈가 완료된 뒤 함께 푸시한다.

## 이슈 완료 기록 양식

### AQ-BE-NNN — 이슈 이름

- **작업 커밋 푸시:** 성공한 원격 브랜치와 작업 HEAD, 확인한 날짜를 기록한다. 실패 시 완료 기록 대신 이슈 목록에 대기 상태를 유지한다.
- **기준 커밋과 작업 커밋:** 해당 이슈의 변경만 구분하고 해시·제목을 나열한다. 완료 기록 커밋 자신의 해시는 최종 보고에서 알린다.
- **목적·문제·가설:** 무엇을 확인하거나 개선할 것인가.
- **개인 담당 범위:** 기존 구현, 이번 구현, 지원받은 범위를 구분한다.
- **대응과 선택 이유:** 무엇을 바꾸고 왜 그 방법을 선택했는가.
- **실험 조건:** 커밋·미커밋 변경, OS·CPU·RAM, 서비스 설정, 데이터 규모·분포, 부하, 워밍업, 반복 횟수.
- **재현 절차:** 실행 위치, 명령, 사전 조건.
- **결과:** 변경 전후의 같은 지표, 성공·실패·건너뜀, 복구 시간이나 중복 건수 등. 측정하지 않은 값은 미측정으로 적는다.
- **증빙:** 코드, 테스트, 설정, 로그, SQL 실행계획, 그래프, CI 링크, 원본 보관 위치.
- **한계와 판단:** 측정 제약, 트레이드오프, 미검증 범위, 채택·미채택 이유.
- **포트폴리오 설명:** 확인된 사실만으로 문제·대응·결과를 적는다. 미완료라면 보류한다.
- **검증 범위와 미완료 항목:** 해당 이슈에서 확인한 범위와 후속 이슈로 넘기는 범위를 구별한다.
- **다음 이슈:** 이어서 수행할 가상 이슈와 첫 커밋 단위.
