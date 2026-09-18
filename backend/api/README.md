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
├─ realtime/                Redis Pub/Sub → 종목별 STOMP WebSocket 중계
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
| `candle` | 확정 분봉 조회, Redis 최근 봉 조회·캐시 보충 | 시세 수집, 확정 봉 DB 쓰기, Kafka 접근 |
| `realtime` | Redis 이벤트 수신·검증, 종목별 WebSocket 중계 | DB 접근, 봉 집계, 재전송 |
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
| `SSAFY_REDIRECT_URI` | 없음 | 백엔드 콜백 URL, SSAFY 사이트에 등록한 값과 같아야 한다. 로컬은 `http://localhost:8080/auth/ssafy/callback` |
| `JWT_SECRET` | 없음 | 서비스 액세스 토큰 서명 키, 32바이트 이상 |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | 로그인 완료 후 브라우저를 돌려보낼 프론트 주소 |
| `AUTH_COOKIE_SECURE` | `false` | HTTPS 배포에서 `true` |
| `SSAFY_BASE_URL` | `https://project.ssafy.com` | |
| `SSAFY_TIMEOUT` | `5s` | |
| `JWT_ISSUER` | `a107` | |
| `JWT_ACCESS_TOKEN_VALIDITY` | `12h` | 액세스 토큰 유효 시간, 쿠키 Max-Age 와 JWT exp 에 함께 쓴다 |
| `UPBIT_BASE_URL` | `https://api.upbit.com` | |
| `UPBIT_TIMEOUT` | `5s` | |
| `REDIS_HOST` | `localhost` | 실시간 Pub/Sub을 구독할 Redis 주소 |
| `REDIS_PORT` | `6379` | Redis 포트 |

Client ID와 Client Secret은 SSAFY project 사이트의 [내 어플리케이션] > [서비스 설정] > [SSAFY 로그인]에서 확인하고, Redirect URI도 같은 화면에 등록한다.

## 인증

SSAFY OAuth2 Authorization Code로 로그인한다. `client_secret`을 가진 서버가 리다이렉트를 직접 받는 confidential client 구조라서 인가 코드가 브라우저 JS에 노출되지 않는다. 로그인 성공 시 서비스 JWT를 httpOnly, SameSite=Lax 쿠키(`access_token`)로 심고 프론트로 302 리다이렉트하며, 실패 시 `{FRONTEND_BASE_URL}/login?error={오류 코드}`로 보낸다. SSAFY 액세스 토큰과 refresh token은 저장하지 않는다.

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/auth/ssafy/authorize-url` | 불필요 | SSAFY 로그인 화면으로 보낼 인가 URL |
| GET | `/auth/ssafy/callback` | 불필요 | SSAFY가 인가 코드를 보내는 콜백, 쿠키를 심고 프론트로 리다이렉트 |
| POST | `/auth/logout` | 불필요 | 토큰 쿠키를 만료시킨다, 로그인 제공자와 무관하다 |
| GET | `/members/me` | 필요 | 인증 주체로 본인 정보 조회 |

보호된 API는 토큰 쿠키 또는 `Authorization: Bearer {accessToken}` 헤더를 받는다. 브라우저는 쿠키가 자동으로 실리므로 프론트가 토큰을 저장할 필요가 없다. 회원 식별자는 요청 본문이 아니라 인증 주체에서 꺼낸다.

## 종목 등록 API

```http
POST /admin/assets
```

요청에는 `exchange`와 `exchangeMarketCode`만 받는다. API가 Upbit 공개 페어 목록에서 실제 거래지원 여부와 종목명을 검증한 뒤 `reference.asset`과 `ops.market_data_collection_target`을 함께 저장한다.

`collectionStatus=ENABLED`는 API가 기록하는 수집 희망 상태다. Go Producer는 이 목록을 주기적으로 읽어 Upbit WebSocket 구독을 갱신하며, 실제 연결 상태를 이 값에 다시 기록하지 않는다.

## 최근 확정 봉 조회

차트 종목 선택은 JWT 인증이 필요한 `GET /assets`를 사용한다(브라우저는 `/api/assets`로 부르고 프록시가 접두사를 뗀다). 등록된 Upbit KRW 종목 중 `TRADING` 상태를 마켓 코드 오름차순으로 반환한다. 응답은 `[{"assetId":"UUID","marketCode":"KRW-BTC","koreanName":"비트코인"}]`이며 없으면 빈 배열이다. 수집 연결·봉 존재를 보장하는 목록은 아니다. Controller → AssetReadService → AssetRepository로 조회하며 REST Docs/OpenAPI에 포함한다.

```http
GET /assets/{assetId}/candles/1m?limit=1000
GET /assets/{assetId}/candles/5m?limit=1000
GET /assets/{assetId}/candles/1440m?limit=1000
```

기존 JWT 쿠키 또는 Bearer 인증으로 호출한다. `assetId`는 종목의 UUID이며, `limit`은 1~1,000(생략 시 1,000)이다. 최신 봉을 선택한 뒤 **봉 시작 시간 오름차순**으로 반환한다.

경로의 `{intervalMinutes}m`은 **1·2·5·15·60·240·1440분**만 지원한다. 기존 `/1m` 호출은 그대로 사용할 수 있다. UTC 버킷을 사용하며 24시간봉(`/1440m`)의 시작은 UTC 00:00(KST 09:00)이다. 지원하지 않는 간격은 `400 COMMON-400-001`이다.

```json
{
  "assetId": "11111111-1111-1111-1111-111111111111",
  "intervalMinutes": 1,
  "candles": [
    {
      "openTimeUnixMilli": 1800000000000,
      "openPrice": "100.123456789012",
      "highPrice": "101",
      "lowPrice": "99",
      "closePrice": "100",
      "tradeVolume": "0.123456789012345678",
      "tradeAmount": "12.12345678",
      "lastEventTimeUnixMilli": 1800000059999
    }
  ]
}
```

- 가격·수량·거래대금은 정밀도를 보존하는 문자열, 시간은 UTC Unix millisecond다. 확정 봉만 반환하며 진행 봉은 `/topic/markets/{marketCode}/candles/{intervalMinutes}m` 구독으로 받는다.
- 확정 봉의 `lastEventTimeUnixMilli`는 실시간 집계 시 체결 시각, REST 보정 시 Upbit의 마지막 틱 저장 시각(상위 봉은 원본 최댓값)이므로 봉 종료 경계를 넘을 수 있다. 봉 구간은 `openTimeUnixMilli`와 간격으로 판단한다. WebSocket live candle의 같은 필드는 현재 반영한 체결 시각이다.
- Redis `candle:{intervalMinutes}m:recent:{assetId}` Sorted Set에 요청 개수만큼 있으면 바로 반환한다. 캐시가 없거나 부족하거나 읽기에 실패하면 PostgreSQL의 해당 종목·간격만 최신 1,000개까지 조회한다. 사용자 조회 시 1분봉 전체를 재집계하거나 별도 count 쿼리를 실행하지 않는다.
- DB 조회 결과는 Go와 같은 JSON 계약으로 캐시에 보충한다. Lua로 비어 있는 score(봉 시작 시간)만 채우고 최신 1,000개를 유지하므로, 이미 캐시에 있는 봉을 오래된 조회 결과로 덮어쓰지 않는다. 캐시 쓰기 실패에도 DB 결과를 반환한다.
- DB에 봉이 적으면 있는 만큼, 없으면 `200`과 빈 `candles`를 반환한다. 별도 종목 존재 조회는 하지 않아 미등록 UUID도 빈 목록이다. 잘못된 UUID·limit은 `400 COMMON-400-001`이다.
- 거래가 없는 시간을 인위적인 봉으로 채우지 않는다. 기간 검색·과거 페이지 조회·진행 봉 스냅샷은 제공하지 않는다. 실제 이력이 limit보다 짧으면 다음 요청도 DB 조회로 최신 이력을 확인한다.
- 상위 봉은 Go Consumer의 공통 주기 작업으로 비동기 저장되며, 원본 1분봉 검증·보정 후 다시 갱신될 수 있다. 이력이 충분하지 않으면 있는 만큼만 반환한다. 종료된 봉이 원본 검증까지 완료되었다는 보장은 아니며 프론트는 1분마다 확정 결과를 재조회한다.

패키지는 Controller → Facade → 캐시 port / DB ReadService로 나눈다. DB 트랜잭션은 cache miss 이후의 ReadService에서만 열고, 캐시 형식·오류 처리는 Redis adapter가 담당한다. 성공·400 응답과 사용법은 RestAssuredMockMvc + REST Docs 테스트에서 OpenAPI로 생성한다.


전용 Redis·PostgreSQL을 준비한 경우 아래 환경변수로 실제 캐시 계약·조회 쿼리 테스트를 추가 실행한다. PostgreSQL은 테스트 전용 `candle_test` DB/사용자/비밀번호를 사용하고 Flyway로 스키마를 적용한다. 공용·운영 인스턴스를 지정하지 않는다.

```powershell
$env:CANDLE_TEST_REDIS_PORT = '16379'
$env:CANDLE_TEST_DB_PORT = '15432'
.\gradlew.bat test --tests 'com.ssafy.a107.candle.*' --rerun-tasks
```

## 현재 팩터 값 조회

```http
GET /assets/{assetId}/factors?timeframe=1D
```

차트가 종목의 현재 팩터 값을 보이는 데 쓴다. 전략 엔진이 선별과 순위에 쓰는 `Ta4jFactorCalculator`를 그대로 불러 같은 값을 계산한다. 화면이 따로 계산하면 사용자가 보고 조건을 정한 숫자와 실제 판단이 어긋난다.

```json
{
  "assetId": "11111111-1111-1111-1111-111111111111",
  "timeframe": "1D",
  "asOf": "2026-09-13T00:00:00Z",
  "factors": [
    { "factor": "RSI", "period": 14, "value": 62.1 },
    { "factor": "RETURN", "period": 20, "value": 4.1 },
    { "factor": "SMA_DISPARITY", "period": 20, "value": -1.8 },
    { "factor": "VOLATILITY", "period": 20, "value": 3.42 },
    { "factor": "VOLUME_RATIO", "period": 20, "value": 1.237 },
    { "factor": "DRAWDOWN", "period": 60, "value": null }
  ]
}
```

- 기간과 순서는 서버가 정한다. RSI 14, 낙폭 60, 나머지 20이며 요청으로 바꿀 수 없다. 응답에 실어 화면이 기간과 차례를 다시 정의하지 않게 한다.
- 값의 단위는 전략 DSL과 같다. 수익률, SMA 이격률, 변동성, 낙폭은 백분율(4.1 = 4.1%)이고 거래량 비율은 배수다. 빌더에서 입력하는 조건 값과 같은 축이라 그대로 비교할 수 있다.
- 봉이 기간을 채우지 못한 팩터는 `value`가 `null`이다. 오류가 아니며 나머지 팩터는 그대로 반환한다.
- `asOf`는 값을 만든 마지막 확정 봉의 시작 시각이다. 봉이 하나도 없으면 `null`이고 모든 값도 `null`이다. 봉 조회와 같이 종목 존재를 따로 확인하지 않아 미등록 UUID도 같은 모양으로 응답한다.
- `timeframe`은 `1H`, `4H`, `1D`만 지원하며 생략하면 `1D`다. 그 밖의 값과 잘못된 UUID는 `400 COMMON-400-001`이다.
- ta4j BarSeries를 만들어야 해 `MinuteCandleReadService.findRecentCandles`로 엔티티를 읽는다. 기간 최댓값을 채울 만큼만 읽고 진행 봉은 쓰지 않는다.
- 거래대금 순위처럼 전 종목 비교가 필요한 값은 이 조회에 넣지 않는다.

## 실시간 시세 WebSocket

API가 기동되면 체결 채널 하나와 지원 간격별 진행 봉 채널 7개를 각각 한 번씩 구독한다. 수신 메시지의 `market_code`와 `interval_minutes`로 목적지를 계산하며, 종목이 추가되어도 설정 변경이나 재기동은 필요 없다. API 인스턴스가 여러 개면 각 인스턴스가 동일한 Redis 이벤트를 받고 자신의 연결에만 중계한다.

| Redis 채널 | STOMP 구독 목적지 | 의미 |
|---|---|---|
| `trade:realtime:updates` | `/topic/markets/{marketCode}/trades` | Kafka 적재 결과와 독립적으로 발행한 실시간 체결 |
| `candle:1m:live:updates` | `/topic/markets/{marketCode}/candles/1m` | Go Consumer가 저장 및 offset 커밋 후 발행한 종목별 최신 진행 봉 |
| `candle:{intervalMinutes}m:live:updates` | `/topic/markets/{marketCode}/candles/{intervalMinutes}m` | 2·5·15·60·240·1440분 진행 봉. 확정 1분봉 합계 + 커밋된 현재 1분봉 |

예를 들어 4시간 차트는 `/topic/markets/KRW-BTC/candles/240m`을 구독한다. 응답 스키마는 1분봉과 같고 `intervalMinutes`가 선택 간격이다. 채널과 payload 간격 불일치, UTC 경계 불일치는 폐기한다. 진행 중 구간의 REST 보정은 Go의 다음 분 합계 갱신에서 반영된다.

### 연결과 여러 종목 구독

- Native WebSocket + STOMP 1.2를 사용한다. 연결 경로는 `/ws`이며 SockJS 경로가 아니다.
- 먼저 로그인하고 **현재 페이지와 같은 오리진**의 `ws://.../ws` 또는 `wss://.../ws`로 연결한다. 브라우저가 `access_token` httpOnly 쿠키를 handshake에 전송한다. STOMP CONNECT 헤더나 URL query에 JWT를 넣지 않는다.
- HTTP handshake에서 토큰이 없거나 만료·위조되면 401, 다른 오리진이면 403으로 거부한다. 브라우저 외 클라이언트는 HTTP handshake의 Bearer 헤더도 사용할 수 있다.
- JWT는 연결 시 검증한다. 이미 열린 연결의 토큰 만료·로그아웃에 따른 강제 종료는 제공하지 않으므로 프론트에서 로그아웃할 때 연결을 종료해야 한다. 재연결은 다시 인증한다.
- 개발 Vite 및 운영 Nginx 설정은 `/ws` WebSocket Upgrade와 same-origin 정보(Host/Origin)를 유지한다. 운영 설정은 배포 시 적용해야 한다.
- `server.forward-headers-strategy: native`로 신뢰하는 프록시의 HTTPS 정보를 반영한다. 운영에서는 API 포트의 loopback 바인딩과 프록시의 전달 헤더 덮어쓰기를 유지한다. 다른 Origin은 계속 403으로 거부한다.

아래는 한 연결에서 BTC 체결, ETH 체결, BTC 진행 봉을 받는 STOMP 프레임 예시다. 프레임마다 마지막 빈 줄 뒤에 실제 NULL 바이트(`\0`)를 붙인다.

```text
CONNECT
accept-version:1.2
host:localhost

```

서버의 `CONNECTED`를 받은 다음 각각 보낸다. `id`는 연결 안에서 구독마다 달라야 한다.

```text
SUBSCRIBE
id:btc-trades
destination:/topic/markets/KRW-BTC/trades
ack:auto

```

```text
SUBSCRIBE
id:eth-trades
destination:/topic/markets/KRW-ETH/trades
ack:auto

```

```text
SUBSCRIBE
id:btc-live-candle
destination:/topic/markets/KRW-BTC/candles/1m
ack:auto

```

ETH 체결만 해제하려면 다음 프레임을 보낸다. BTC 구독은 유지된다.

```text
UNSUBSCRIBE
id:eth-trades

```

종목 코드는 `KRW-` 뒤에 영문 대문자·숫자가 오는 형식이다. 정확한 종목 목적지만 허용하고 `*`, `**`, `{marketCode}` 같은 패턴 구독은 거부한다. DB에서 종목 존재 여부를 조회하지 않으므로 해당 종목의 수집 이벤트가 없으면 메시지도 오지 않는다.

클라이언트 `SEND` 및 잘못된 구독은 STOMP `ERROR` 후 연결을 닫는다. ERROR의 `message` 헤더는 `REALTIME-400-001`, body는 `허용되지 않은 실시간 구독 요청입니다.`다. 해당 스트림은 `ack:auto`를 사용하며 client ACK/NACK와 트랜잭션 명령은 지원하지 않는다.

### 응답 계약

각 STOMP `MESSAGE`의 body는 다음 camelCase JSON이다. 가격·거래량·거래대금은 정밀도를 보존하는 **문자열**, UUID는 문자열, 시간은 UTC Unix millisecond 정수다. 순번은 64비트 정수이므로 클라이언트에서 정밀도에 주의한다.

체결 응답:

```json
{
  "assetId": "11111111-1111-1111-1111-111111111111",
  "marketCode": "KRW-BTC",
  "price": "100000000.12345678",
  "volume": "0.00000001",
  "side": "BID",
  "tradedAtUnixMilli": 1800000000123,
  "sequenceId": 123456789
}
```

`side`는 `ASK`(매도) 또는 `BID`(매수)이며 가격·수량은 양수다. `sequenceId`는 체결 식별 순번이다.

진행 봉 응답:

```json
{
  "assetId": "11111111-1111-1111-1111-111111111111",
  "marketCode": "KRW-BTC",
  "intervalMinutes": 1,
  "openTimeUnixMilli": 1800000000000,
  "openPrice": "100000000.12345678",
  "highPrice": "100000001",
  "lowPrice": "99999999",
  "closePrice": "100000000",
  "tradeVolume": "0.00000001",
  "tradeAmount": "1.0000000012345678",
  "lastEventTimeUnixMilli": 1800000000123,
  "lastSequenceId": 123456789
}
```

`openTimeUnixMilli`는 봉 시작 시간이다. 동일한 종목·시작 시간의 봉은 수신 값으로 갱신한다. OHLC는 시가·고가·저가·종가, `tradeVolume`과 `tradeAmount`는 해당 봉 누적 거래량·거래대금이다. `lastEventTimeUnixMilli`와 `lastSequenceId`는 마지막 반영 체결을 나타낸다. 이는 **진행 봉 상태**이며 확정 봉 저장 알림이 아니다.

### 전달과 책임

- Redis 수신 DTO는 `realtime/infrastructure/redis`, 내부 이벤트는 `realtime/domain`, 발행 경계는 `realtime/service/RealtimeEventPublisher`에 둔다. WebSocket adapter는 응답 DTO로 변환해 내장 STOMP 브로커에 발행한다.
- Redis 수신은 단일 실행 스레드로 순서를 유지한다. 공용 대기 큐는 2,048건으로 제한하며 가득 차면 가장 오래 대기한 알림을 버리고 최신 갱신을 받는다. 사용자별 재전송 버퍼는 없다.
- 필수 필드·UUID·종목 코드·ASK/BID·양수 시간/순번·지원 간격·UTC 버킷 경계를 검증한다. 잘못된 메시지와 중계 실패는 원문 없이 경고 로그를 남기고 이후 수신을 계속한다.
- Pub/Sub은 best-effort다. 중단·과부하 중 빠진 메시지는 재전송하지 않으며 체결과 진행 봉 채널 사이의 동기화도 보장하지 않는다. 초기 확정 봉은 선택 간격의 최근 봉 조회 API로 읽는다. 진행 봉 스냅샷 복구는 별도 작업이다.

기본 `build`는 외부 Redis 없이 실제 내장 서버의 JWT handshake, 종목별 구독/해제, 메시지 라우팅과 보안 정책을 검증한다. 전용 테스트 Redis의 로컬 포트를 지정하면 실제 Pub/Sub 및 다중 인스턴스 테스트도 실행한다. 운영/공용 Redis를 테스트 대상으로 사용하지 않는다.

```powershell
$env:REALTIME_TEST_REDIS_PORT = '16379'
.\gradlew.bat test --tests 'com.ssafy.a107.realtime.*' --rerun-tasks
```

WebSocket/STOMP 계약은 이 절에서 관리하며 REST Docs/OpenAPI에는 포함하지 않는다.

## 모의투자 재선정과 확정 봉 준비

모의투자는 진행 봉 이벤트를 실행 신호로 받지만, 전략 판단에는 실행 시각 직전의 확정 봉만 사용한다. Go의 상위 봉 저장은 비동기이므로 새 진행 봉이 도착했어도 직전 확정 봉은 아직 DB에 없을 수 있다.

- 워밍업 구간은 `max(ranking.period, 필터의 모든 FACTOR period) + 1`개다. 예를 들어 일봉 기간 20 전략은 실행 시각 이전 21개 일봉이 필요하다.
- 거래지원 시작일이 워밍업 시작 이후인 신규 종목은 계산 대상에서 제외한다. 상장 전 봉은 기다려도 생기지 않기 때문이다. 시작일을 모르는 종목은 실제 봉 이력으로 판단한다.
- 마지막 확정 봉을 제외한 과거 워밍업 이력이 충분한 종목만 이번 재선정의 데이터 준비 대상으로 삼는다. 일봉 기간 20이면 앞의 20개 일봉이 필요하다. 과거 이력부터 부족한 종목은 계산에서 제외하므로, 수집이 멈춘 한 종목이 다른 종목의 재선정을 계속 막지 않는다. 거래지원·수집 상태 자체를 변경하거나 상장폐지로 추정하지는 않는다.
- 위 대상 중 마지막 확정 봉이 아직 저장되지 않은 종목이 하나라도 있으면 `NOT_READY`로 재시도한다. 마지막 봉의 저장 순서에 따라 순위가 달라지지 않도록 모두 기다린다.
- 계산 가능한 종목이 하나도 없거나, 후보에 포함된 기존 보유 종목의 과거 이력이 부족하면 대기한다. 보유 종목의 평가·체결 가격이 없어도 기존 가격 검사를 통해 대기한다. 데이터 부족만으로 보유를 청산하거나 과거 가격을 현재 가격으로 대신하지 않는다.
- 대기 중에는 해당 실행 시각의 `lastCandleAt`, `lastRebalanceAt`, 현금, 보유 종목, 체결·평가·후보군 기록을 변경하지 않는다. 기존 리스너가 재시도하며, 데이터가 준비되면 같은 실행 시각을 처리한다. 이미 처리한 시각의 중복 이벤트는 무시한다.
- 데이터가 모두 준비되어도 필터를 통과한 종목이 없을 수 있다. 이때는 정상적인 현금 보유 재선정으로 완료한다.

이 구분은 봉 이력에 따른 계산 정책이며 수집 장애 원인을 판정하지 않는다. 과거 공백이 보충되면 다음 판단부터 해당 종목도 다시 계산 대상이 된다. 마지막 봉만 계속 없는 종목이나 데이터가 부족한 보유 종목은 여전히 대기를 유발하므로 수집·보정 상태를 점검해야 한다. 이 변경은 과거에 이미 완료 처리된 재선정을 되돌리거나 운영 기록을 자동으로 재실행하지 않는다.

### 즉시 준비 상태 진단

아래 읽기 전용 API는 별도 프로필이나 환경변수 설정 없이 사용할 수 있다. 호출자는 자신의 모의투자만 확인할 수 있고, 실행·재선정 시각·체결·보유·평가 기록을 바꾸지 않는다.

```http
GET /debug/simulations/{simulationId}/readiness?executionAt=2026-09-18T00:00:00Z
```

응답의 전체 `state`가 `READY`면 해당 시각의 전략 판단에 필요한 봉이 준비됐다는 뜻이다. 종목별 `state`는 마지막 봉을 기다리는지, 과거 이력이 부족해 제외되는지, 신규 상장이라 제외되는지를 보여 준다. `hideLatestCompletedCandle=true`을 추가하면 마지막 확정 봉이 아직 저장되지 않은 상황을 메모리에서만 재현한다. 이 옵션도 기록을 바꾸지 않으며, 같은 요청을 옵션 없이 다시 호출해 `WAITING_FOR_CANDLES`와 `READY`를 즉시 비교할 수 있다.

### 강제 재선정 검증

아직 봉을 한 번도 처리하지 않은 실행 중 모의투자는 아래 API로 과거 확정 봉 시점의 재선정을 한 번 즉시 재생할 수 있다. 판단에는 직전 확정 봉 종가를, 체결에는 요청 시각의 봉 시가를 사용하며 일반 재선정과 같이 체결·보유 종목·평가자산·후보군 기록을 저장한다.

```http
POST /debug/simulations/{simulationId}/rebalances?executionAt=2026-09-18T00:00:00Z
```

- 호출자는 자신의 모의투자만 실행할 수 있다.
- 첫 봉을 이미 처리한 모의투자는 `ALREADY_PROCESSED`로 응답하며 중복 체결하지 않는다. 강제 검증에는 새 모의투자를 사용한다.
- 요청 시각이 시작 시각보다 과거면 아직 봉 이력이 없는 실행의 시작 시각을 요청 시각으로 맞춘다.
- 과거 확정 봉 또는 시가가 아직 없으면 `WAITING_FOR_CANDLES`로 응답하고 체결·보유 종목·평가·후보군 기록을 만들지 않는다.
- 서버 로그에는 재선정 대기 사유, 준비된 후보 수, 선정 종목 코드, 주문 수와 체결 수를 남긴다.

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
