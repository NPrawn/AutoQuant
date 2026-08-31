# backend/api

> Spring Boot API 서버. Java 21 · Spring Boot 3.5.16 · Gradle 단일 프로젝트.

Gradle이 소유하는 디렉토리는 여기까지다. 형제인 `../ingest/`는 Go 모듈이며 이 빌드와 의존성이 없다.

## 패키지 구성

```
src/main/java/com/ssafy/a107/
├─ A107Application.java     @SpringBootApplication   :8080
├─ common/                  공통 엔티티 · 예외 처리
├─ member/                  서비스 회원 계정
├─ asset/                   거래소 종목 기준 정보 · 수집 대상 등록 API
├─ auth/                    JWT 발급 · 검증
├─ candle/                  확정 분봉 도메인 · 조회
└─ trading/                 모의투자 · 백테스팅

src/main/resources/
├─ application.yml
└─ db/migration/            Flyway — DB 스키마의 단독 원본
```

## 패키지 경계

모듈이 하나라 컴파일러가 경계를 강제하지 않는다. **패키지 규칙으로 지킨다.**

| 패키지 | 담는 것 | 담지 않는 것 |
|---|---|---|
| `common` | 공통 엔티티, 예외 코드, 전역 예외 처리 | 기능별 비즈니스 로직 |
| `member` | 외부 로그인 식별자, 회원 프로필·상태 | 비밀번호, 인증 제공자 토큰 |
| `asset` | 거래소 마켓, 기준·상대 통화, 거래지원 상태 | 회원 잔액, 포지션 |
| `auth` | SSAFY SSO 연동, JWT 발급·검증 | 회원 프로필, 비밀번호·인증 제공자 토큰 저장 |
| `candle` | 확정 분봉 엔티티·조회 | 시세 수집, Redis·Kafka 쓰기 |
| `trading` | 모의투자 주문·체결, 백테스팅 엔진 | 시세 수집, 랭킹 |

### 의존 방향

```
candle ──┐
asset ───┤
member ──┼──> common
trading ─┤
auth ────┘
```

- **`common`은 기능 패키지에 의존하지 않는다.**
- **`member`는 서비스 회원, `asset`은 거래 가능한 마켓 기준 정보다.** 회원 잔액을 `asset`에 저장하지 않는다.
- **`candle` ↔ `trading` 상호 참조 금지.** 공유가 필요하면 `common` 중 맞는 곳으로 내린다.
- **확정 분봉 쓰기는 Go consumer 전용이다.** JVM이 쓰면 데이터 출처가 갈라진다.

## 규칙

1. **`ddl-auto`는 `validate`.** Go consumer가 같은 테이블에 INSERT하므로 Hibernate가 스키마를 변경하면 안 된다.
2. **DB 스키마는 Flyway가 단독 소유.** 적용된 마이그레이션 파일은 수정하지 않고 `V{n}__*.sql`을 추가한다.
3. **`common`은 기능 패키지에 의존하지 않는다.**
4. **`candle`과 `trading`은 서로를 참조하지 않는다.**
5. **확정 분봉은 Go consumer만 쓴다.**

## 종목 등록 API

```http
POST /api/admin/assets
```

요청에는 `exchange`와 `exchangeMarketCode`만 받는다. API가 Upbit 공개 페어 목록에서 실제 거래지원 여부와 종목명을 검증한 뒤 `reference.asset`과 `ops.market_data_collection_target`을 함께 저장한다.

`collectionStatus=ENABLED`는 수집 대상으로 등록됐다는 뜻이며 실제 Go Producer의 WebSocket 구독 완료 상태는 아니다. Go Producer 연동 전까지 이 테이블은 API가 기록하는 희망 상태만 표현한다.

## API 문서

Controller 테스트는 `RestAssuredMockMvc`로 실행하고 REST Docs 리소스 스니펫을 생성한다. 성공·오류 응답은 각각 문서화하며 `openapi3` 작업이 동일 API의 응답들을 하나의 OpenAPI 3 YAML로 합친다.

```bash
./gradlew openapi3
```

산출물은 `build/api-spec/openapi.yaml`이며 Swagger Editor와 Swagger UI에서 바로 읽을 수 있다. stale snippet이 남지 않도록 최종 문서는 `./gradlew clean openapi3`으로 생성하며 `build/` 생성물은 커밋하지 않는다.

## 명령

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew openapi3   # 테스트 기반 OpenAPI 3 YAML 생성
./gradlew bootRun    # :8080
./gradlew dependencies --configuration compileClasspath
```
