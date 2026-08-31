# Git 컨벤션

이 문서는 프론트엔드, 백엔드 API, Go 파이프라인, AI 서버를 포함한 저장소 전체의 공통 Git 규칙이다.

## 브랜치와 커밋

- 작업 브랜치는 `{type}/S15P21A107-{number}` 형식을 사용한다. 예: `feat/S15P21A107-128`.
- 새 작업 브랜치는 `develop`을 기준으로 만든다.
- Conventional Commits를 사용한다. 허용 type은 `feat`, `fix`, `refactor`, `chore`, `docs`, `style`, `test`, `perf`, `build`, `ci`, `revert`다.
- 커밋 메시지는 `type(scope): summary` 또는 `type: summary` 형식을 사용한다.

## Merge Request

- `origin/develop...HEAD` diff를 기준으로 MR 본문을 작성한다.
- 변경 구현이 아니라 사용자·시스템 관점의 동작을 `Summary`, `Changes`로 설명한다.
- 배포 또는 검토 주의 사항이 있을 때만 `Note`를 추가한다.
- Jira 키가 확인되면 `Related Issue`에 `https://ssafy.atlassian.net/browse/{key}`를 추가한다.
- MR 제목은 `[TYPE] Jira summary` 형식을 사용한다. Jira 정보를 확인할 수 없으면 제목을 사용자에게 요청한다.
- 기존 source branch MR이 있으면 본문을 갱신하고, 없으면 target branch `develop`으로 생성한다.
