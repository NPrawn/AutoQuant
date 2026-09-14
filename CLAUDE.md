# S15P21A107 Project Instructions

이 파일은 저장소 전체에 적용하는 공통 규칙이다. 기술·패키지·테스트 같은 세부 규칙은 작업 대상 모듈의 `CLAUDE.md`를 따른다.

## 프로젝트

Upbit KRW 시장을 우선 지원하는 암호화폐 전략 설계·백테스트·모의투자 플랫폼이다.

| 경로 | 역할 | 상세 지침 |
|---|---|---|
| `backend/api` | Spring REST API와 DB 스키마 | `backend/api/CLAUDE.md` |
| `backend/ingest` | Upbit·Kafka·Redis·PostgreSQL 파이프라인 | `backend/ingest/CLAUDE.md` |
| `ai` | 자연어를 전략 DSL로 변환하는 FastAPI | `ai/CLAUDE.md` |
| `frontend` | React 사용자 UI | `frontend/CLAUDE.md` |
| `infra` | 로컬 Docker 인프라 | `infra/README.md` |

현재는 설계와 기반 코드 중심의 초기 구현 단계다. 계획 문서에 있다는 이유만으로 기능이 구현됐다고 가정하지 않는다.

## 모듈 경계

- Frontend는 Spring API만 호출하고 DB·Redis·Kafka·AI 서버에 직접 접근하지 않는다.
- AI 서버는 Spring에서만 호출하며 DB·Redis에 직접 연결하지 않는 무상태 서비스다.
- Java와 Go는 소스 타입을 공유하지 않고 PostgreSQL 스키마와 Redis 포맷을 계약으로 사용한다.
- DB 스키마의 단독 원본은 `backend/api/src/main/resources/db/migration`의 Flyway SQL이다.
- 시장 원본 이벤트는 Kafka, 진행 중 봉은 Redis, 확정 분봉은 PostgreSQL에 둔다.
- 확정 분봉은 Go Consumer가 쓰고 Spring은 읽는다.

## 공통 작업 규칙

- 작업 전 대상 모듈의 `CLAUDE.md`, README, 관련 컨벤션을 읽는다.
- 기존 사용자 변경과 관련 없는 파일을 되돌리거나 정리하지 않는다.
- 지정되지 않은 라이브러리·프레임워크는 도입 전에 확인한다.
- `.env`, API 키, 비밀번호, 토큰을 커밋하거나 로그·응답에 노출하지 않는다.
- `bin`, `build`, `dist`, 캐시 등 생성물을 직접 수정하거나 커밋하지 않는다.
- 시간과 데이터 계약처럼 모듈을 넘는 변경은 관련 모듈과 문서를 같은 변경에서 함께 갱신한다.
- 구조·계약·패키지가 바뀌면 관련 README와 지침 파일을 함께 수정한다.

## 공통 문서

- 브랜치·커밋·MR: `docs/git-convention.md`
- 서비스 기획: `docs/서비스 기획서.md`
- 백테스트·모의투자 ERD: `docs/backtest_simulation_erd.md`
- 모듈을 넘는 설계 결정 기록(ADR): `docs/adr/`

Git 작업 전에는 `docs/git-convention.md`를 읽는다. Git 규칙을 모듈별 지침에 복제하지 않는다.

## 검증

변경한 모듈의 지침에 정의된 빌드·테스트·정적 검사를 실행한다. 도구가 없거나 실행할 수 없다면 임의로 설치하지 말고 미검증 항목과 이유를 완료 보고에 명시한다.

## 지침 동기화

Codex용 루트 `AGENTS.md`는 이 파일과 같은 공통 범위만 유지한다. 모듈 세부사항은 각 디렉터리의 지침에 두고 루트로 끌어올리지 않는다.
