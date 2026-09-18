# ai/docs

AI 서버 문서. 코드와 같은 저장소에 두어 함께 버전 관리한다.

| 파일 | 내용 | 언제 읽나 |
|---|---|---|
| [PLAN.md](PLAN.md) | 전체 구현 계획 | 이 서버가 왜 이렇게 생겼는지 알고 싶을 때 |
| [TASKS.md](TASKS.md) | 작업 분해 (Epic A ~ L) | 무엇을 어느 순서로 만들지 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 구조와 흐름 다이어그램 | 요청 하나가 어느 모듈을 어떤 순서로 지나는지 |
| [DECISIONS.md](DECISIONS.md) | **결정 로그 겸 트레이드오프 기록** | "왜 A가 아니라 B인가" |
| [HANDOFF-A.md](HANDOFF-A.md) | Epic A + C 인계 명세 (완료) | 기반·오류·로깅이 왜 이렇게 생겼는지 |
| [HANDOFF-B.md](HANDOFF-B.md) | E-1·E-2·D-1·J-1 인계 명세 (완료) | 프롬프트 로더·정제가 왜 이렇게 생겼는지 |
| [HANDOFF-C.md](HANDOFF-C.md) | 스키마 상한 정리 + Epic H 인계 명세 (완료) | API가 왜 이렇게 생겼는지 |
| [HANDOFF-D.md](HANDOFF-D.md) | 프롬프트 도메인 규칙 + 측정 준비 인계 명세 | 지금 티켓을 잡았을 때 |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | 겪은 문제와 해결 | 같은 데서 또 막혔을 때 |
| [contract/](contract/) | OpenAPI·DSL JSON Schema·응답 예시 5건 | 백엔드·프론트가 계약을 볼 때. **손으로 고치지 않는다** — `python scripts/export_contract.py`가 만든다 |
| [REVIEW-MOCKUP.md](REVIEW-MOCKUP.md) | 화면 시안 피드백 (AI 서버 관점) | 기획과 계약을 맞출 때 |
| [REVIEW-STRATEGY-MVP.md](REVIEW-STRATEGY-MVP.md) | 전략팀 MVP 문서 검토 (AI 서버 관점) | 전략 도메인 스펙과 계약을 맞출 때 |
| [REVIEW-STRATEGY-SPEC.md](REVIEW-STRATEGY-SPEC.md) | 전략 도메인 확정 명세 검토 (AI 서버 관점) | 확정 명세와 구현이 어긋나는 지 볼 때 |
| [PROPOSAL-FACTORS.md](PROPOSAL-FACTORS.md) | Factor 18개 제안 (ta4j 기준) | 팩터를 늘릴 때, 엔진에 지표를 붙일 때 |
| [DESIGN-PROMPT-AXES.md](DESIGN-PROMPT-AXES.md) | 프롬프트를 관용구 표에서 팩터 축 정의로 옮기는 설계와 검증 계획 | 변환 품질을 고칠 때, 일반화를 측정할 때 |
| [PLAN-PROMPT-AXES.md](PLAN-PROMPT-AXES.md) | 위 설계의 구현 계획 (작업 7건) | 그 작업을 실제로 잡았을 때 |
| [PLAN-CONTEST-DATA.md](PLAN-CONTEST-DATA.md) | 대회 데이터 생성·검증·로컬 적재 설계 (결정은 `adr/`와 `../../docs/adr/`) | 대회 데이터 코드를 만들거나 검증 결과를 볼 때 |
| [adr/](adr/) | 대회 데이터 생성기 내부 결정: 조작 방식(0008·0009·0011)과 검증(0012·0015) | 생성·검증 방식을 바꿀 때 |
| [contest-validation/](contest-validation/) | 대회 데이터 1·2단계 확인과 로컬 대회 결과 JSON | 조작 데이터가 어디까지 검증됐는지 볼 때 |

## 문서 경계

**결정과 트레이드오프는 한 파일이다.** `DECISIONS.md`에 선택지·근거·확정이 같이 있다. 트레이드오프만 따로 빼면 결정과 근거가 갈라져서 나중에 어느 쪽이 최신인지 알 수 없게 된다.

**대회 데이터 결정은 ADR로 남기고 위치를 나눈다.** 생성기 안에서 끝나는 결정(조작 방식, 검증)은 `adr/`에, 백엔드·화면과 맞물린 결정(채점, 저장, 제출 규칙)은 루트 `docs/adr/`에 둔다. 번호와 형식은 루트 [ADR 목록](../../docs/adr/README.md) 하나를 따른다.

**DSL 스펙 문서를 손으로 쓰지 않는다.** 필드명과 허용값은 `app/core/schema.py` 한 곳에만 적는다. 백엔드에 넘길 계약 문서(OpenAPI 명세, DSL JSON Schema, 예시)는 L-3에서 스크립트로 코드에서 뽑는다. 손으로 쓴 스펙은 코드와 갈라지는 순간 거짓말이 된다.

`docs/contract/`가 그 산출물이다. 갈라짐은 규칙이 아니라 테스트가 막는다 — 스크립트를 다시 돌린 결과가 커밋된 파일과 다르면 `tests/test_contract_export.py`가 실패한다.

## Jira

프로젝트 `S15P21A107`. AI 에픽은 [S15P21A107-12](https://ssafy.atlassian.net/browse/S15P21A107-12)(완료), [S15P21A107-131](https://ssafy.atlassian.net/browse/S15P21A107-131)(완료), [S15P21A107-215](https://ssafy.atlassian.net/browse/S15P21A107-215)(대회 데이터, 진행 중)이다. 목록과 규칙은 [../CLAUDE.md](../CLAUDE.md) Jira 절에 있다.

티켓 요약은 `TASKS.md`의 작업 ID로 시작한다 (예: `A-1 프로젝트 초기화`). 보드와 문서가 갈라지지 않게 하려는 것이다.

## 갱신 규칙

- 결정이 확정되면 `DECISIONS.md` 확정 로그에 **날짜와 함께** 적고, 영향받는 티켓 설명을 같이 고친다. 문서만 고치고 티켓을 두면 엔지니어가 옛 지시를 본다
- 막혔다 풀리면 `TROUBLESHOOTING.md`에 적는다. **풀린 직후에 적는다.** 나중에 적으면 증상 문구를 잊는다
- `PLAN.md`와 `TASKS.md`는 계획이 실제로 바뀔 때만 고친다. 진행 상태는 Jira가 들고 있다
