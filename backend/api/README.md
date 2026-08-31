# backend/api

> Spring Boot API 서버. Java 21 · Spring Boot 3.5.16 · Gradle 단일 프로젝트.

Gradle이 소유하는 디렉토리는 여기까지다. 형제인 `../ingest/`는 Go 모듈이며 이 빌드와 의존성이 없다.

## 패키지 구성

```
src/main/java/com/ssafy/a107/
├─ A107Application.java     @SpringBootApplication   :8080
├─ common/                  공통 엔티티 · 예외 처리
├─ member/                  서비스 회원 계정
├─ asset/                   거래소의 거래 자산 기준 정보
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

## 환경변수

기본값이 없는 값은 비어 있으면 애플리케이션이 기동되지 않는다. `.env`나 실행 환경에서만 주입하고 저장소에 커밋하지 않는다.

| 이름 | 기본값 | 설명 |
|---|---|---|
| `SSAFY_CLIENT_ID` | 없음 | SSAFY project 사이트에서 발급받은 Client ID |
| `SSAFY_CLIENT_SECRET` | 없음 | 토큰 발급 시 함께 보내는 비밀값 |
| `SSAFY_REDIRECT_URI` | 없음 | 인가 코드를 받을 URL, SSAFY 사이트에 등록한 값과 같아야 한다 |
| `JWT_SECRET` | 없음 | 서비스 액세스 토큰 서명 키, 32바이트 이상 |
| `SSAFY_AUTHORIZE_URL` | `https://project.ssafy.com/oauth/sso-check` | |
| `SSAFY_TOKEN_URL` | `https://project.ssafy.com/ssafy/oauth2/token` | |
| `SSAFY_USER_INFO_URL` | `https://project.ssafy.com/ssafy/resources/userInfo` | |
| `JWT_ISSUER` | `a107` | |
| `JWT_ACCESS_TOKEN_VALIDITY` | `1h` | |
| `SSAFY_CONNECT_TIMEOUT` | `3s` | |
| `SSAFY_READ_TIMEOUT` | `5s` | |

Client ID와 Client Secret은 SSAFY project 사이트의 [내 어플리케이션] > [서비스 설정] > [SSAFY 로그인]에서 확인하고, Redirect URI도 같은 화면에 등록한다.

## 인증

SSAFY OAuth2 Authorization Code로 로그인하고, 서비스는 자체 JWT를 발급한다. SSAFY 액세스 토큰과 refresh token은 저장하지 않는다.

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/auth/ssafy/authorize-url` | 불필요 | SSAFY 로그인 화면으로 보낼 인가 URL |
| POST | `/auth/ssafy/tokens` | 불필요 | 인가 코드를 서비스 액세스 토큰으로 교환, 미가입이면 가입 |
| GET | `/members/me` | 필요 | 인증 주체로 본인 정보 조회 |

보호된 API는 `Authorization: Bearer {accessToken}` 헤더를 받는다. 회원 식별자는 요청 본문이 아니라 인증 주체에서 꺼낸다.

## 명령

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun    # :8080
./gradlew dependencies --configuration compileClasspath
```
