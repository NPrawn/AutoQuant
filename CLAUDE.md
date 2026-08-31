# Project Agent Rules

이 리포지토리에서 코드를 생성·리뷰·수정할 때 적용한다.

## General

- 변경하는 영역에 맞는 컨벤션을 따른다.
- 지정되지 않은 기술은 도입 전에 확인한다.
- 기존 프로젝트 구조와 코딩 스타일에 맞춘다.
- 매직 스트링, 사용하지 않는 import·코드·불필요한 파일을 남기지 않는다.
- 빌드 명령이 있는 영역은 변경 후 빌드/테스트를 실행한다.

## Git

@backend/docs/convention/git-convention.md

## 영역별 컨벤션

컨벤션 원본은 `backend/docs/convention/`에 있고, 영역별 CLAUDE.md가 해당 영역 작업 시 자동 로드한다.

- `backend/api` (Java / Spring Boot): [backend/api/CLAUDE.md](backend/api/CLAUDE.md) — Java·Spring Boot·JPA·테스트 컨벤션
- `backend/ingest` (Go 파이프라인): [backend/ingest/CLAUDE.md](backend/ingest/CLAUDE.md) — Go·Go 테스트 컨벤션
