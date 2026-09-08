# frontend

> 암호화폐 전략 플랫폼 UI. **디자인 시스템 이관 완료 — 화면 구현 진행 중.**

React 19 + TypeScript + Vite.

## 스택

| 항목 | 버전 |
|---|---|
| React | 19 |
| TypeScript | 6 |
| Vite | 8 |
| 린터 | oxlint (템플릿 기본) |
| 라우터 | react-router 7 |
| 서버 상태 | TanStack Query 5 |

차트는 `lightweight-charts`, 실시간 STOMP 연결은 `@stomp/stompjs`를 사용한다.

## 디자인 시스템

화면 목업 29개에 같은 스타일시트가 내장돼 있어 한 벌만 `src/styles`로 옮겼다. 화면을 만들 때는 이 클래스를 재사용하고 스타일을 다시 쓰지 않는다.

| 파일 | 내용 |
|---|---|
| `src/styles/tokens.css` | 색·반경·그림자 CSS 변수, 다크·라이트 두 팔레트 |
| `src/styles/base.css` | 리셋, 공통 클래스, 화면별 컴포넌트 스타일 |
| `src/styles/overrides.css` | 목업(button)과 React 마크업(a) 차이 보정 |
| `src/styles/extensions.css` | 목업에 없는 상태 변형, 예: 오류 콜아웃 `.callout.crit` |
| `src/styles/theme.ts` | `data-theme` 속성 전환과 선택 저장 |

**색에 의미가 배정돼 있다.** 한국 시장 관례를 따라 빨강(`--up`)이 상승이므로 오류에는 주황(`--crit`)을 쓴다. 파랑(`--down`)은 하락, 초록(`--good`)은 정상, 보라(`--dd`)는 낙폭이다. 흰색은 선택·포커스 같은 UI 상태를 뜻하고 금융 의미를 담지 않는다.

테마는 문서 루트의 `data-theme` 속성으로만 바뀐다. 첫 페인트에서 다크 화면이 번쩍이지 않도록 `index.html`의 인라인 스크립트가 마운트 전에 복원한다.

## 구조

```text
src/
├── app/
│   ├── router.tsx        라우트 정의, handle.title 이 상단바 제목
│   ├── routes.ts         경로 상수와 링크 생성 함수
│   ├── navigation.tsx    사이드바 항목
│   └── layout/           AppShell, Sidebar, Topbar, AccountMenu, LogoutConfirmModal
├── api/                  request 함수, ApiError
├── auth/                 인증 API, 회원 모델, useCurrentMember, RequireAuth, GuestOnly, 세션 만료 처리
├── components/           LoadingState, EmptyState, ErrorState, ConfirmDialog
├── strategy/             전략 DSL 계약 타입, 표시 문구, 빌더 편집 상태(순수 함수), 빌더 진입 상태
│   ├── builder/          StrategyEditor(빌더 본체)와 블록 컴포넌트(조건·순위·재선정·요약 카드)
│   └── assistant/        전략봇 패널과 대화 상태
│   (api.ts 변환·저장·조회·삭제 호출, queries.ts 쿼리·뮤테이션, summary.ts DSL 요약 문장, *ErrorMessage.ts 실패 문구)
├── pages/                화면, 목업 번호와 1:1
├── market/               종목·봉 API, 쿼리, STOMP 연결, 데이터 검증·병합, 1분봉 렌더러
├── styles/               디자인 시스템
└── mocks/                API 연동 전 표시용 더미
```

로그인은 사이드바가 없어 `AppShell` 바깥에 있고, 나머지 화면은 셸 안의 `<Outlet />`에 그려진다. 960px 이하에서 사이드바가 접히는 규칙은 목업 CSS 가 그대로 담당한다.

## 라우팅

경로는 `src/app/routes.ts`에 모여 있고 화면은 `src/pages`에 목업 번호와 1:1로 대응한다.

| 경로 | 화면 | 목업 |
|---|---|---|
| `/login` | 로그인 | 01 |
| `/` | 전략 만들기 | 02 |
| `/chart` | 차트 | 04 |
| `/strategies` | 전략 관리 | 05, 23 |
| `/strategies/new`, `/strategies/:id/edit` | 전략 구성 | 19, 22, 27 |
| `/strategies/:id`, `/strategies/:id/strategy`, `/strategies/:id/code` | 전략 상세 탭 | 07, 08, 09 |
| `/ranking` | 전략 랭킹 | 06, 26 |
| `/backtest` | 백테스트 | 10, 11, 12, 24 |
| `/simulations` | 모의투자 | 16, 17 |
| `/simulations/:id` | 모의투자 실시간 | 18, 25 |
| `/contests` | 대회 | 30 |
| `/contests/:id` | 대회 상세 | 31, 36 |
| `/contests/:id/result` | 대회 결과 | 32, 33, 35 |
| `/notifications` | 알림 | 37 |

계정 메뉴(28)와 로그아웃 확인(29)은 경로가 아니라 상단바 `AccountMenu`가 여는 오버레이다. 로그아웃 확인은 네이티브 `dialog`라 포커스 트랩과 Esc 를 브라우저가 처리한다.

## 인증 흐름

```text
로그인 버튼 → GET /api/auth/ssafy/authorize-url → SSAFY 로 이동
SSAFY → GET :8080/auth/ssafy/callback?code= → access_token 쿠키 심고 302 → /
RequireAuth → GET /api/members/me → 200 이면 셸 렌더, 401 이면 /login
로그아웃 → POST /api/auth/logout (쿠키 만료) → 캐시 비우고 /login
어떤 API 든 401 → 회원 쿼리를 "로그인 안 됨"으로 → RequireAuth → /login?error=AUTH-401-003
```

토큰은 `httpOnly` 쿠키라 JS 가 읽지 못한다. 그래서 `/members/me` 응답이 로그인 상태의 유일한 근거이고, 프론트에 토큰 저장 코드가 없다. 쿠키는 `localhost:8080`이 심지만 브라우저 쿠키는 포트를 구분하지 않아 `localhost:5173` 요청에도 실린다.

콜백이 실패하면 백엔드가, 세션이 끊기면 `RequireAuth`가 `/login?error={코드}`로 보낸다. 코드 상수는 `src/api/errorCodes.ts`, 코드별 문구는 `src/auth/loginErrorMessage.ts`에 있다. 처음부터 로그인이 없던 방문은 안내 없이 로그인 화면만 보인다.

## 전략 변환 흐름

```text
홈 입력 → /strategies/new (state.prompt) → POST /api/ai/strategies/translate { userInput }
        → 응답 strategy 로 블록을 채우고 meta.changes/assumptions/warnings 를 전략봇 말풍선에 붙인다
전략봇 입력 → POST … { userInput, currentStrategy: 지금 블록 } → 같은 방식으로 블록 교체
전략 저장 → validateDraft 통과 → POST /api/strategies { name, description?, strategyDsl } → 201 → /strategies/:id
         → 실패 시 블록 자리에 ErrorState(400 은 필드별 항목), 편집 내용 유지
```

조건이 하나도 없는 전략은 `currentStrategy`를 보내지 않는다. 백엔드가 빈 조건 그룹을 거부하기 때문이다. 변환 실패 문구는 `src/strategy/translateErrorMessage.ts`에 있고, 첫 변환이 실패하면 블록 위에 재시도 안내를 보인다.

## 전략 목록·상세

```text
/strategies              GET /api/strategies → 표. 상태·수익률·낙폭은 백테스트 결과 API 가 없어 초안·빈 값
/strategies/:id[/tab]    GET /api/strategies/:id → 성과(빈 상태) · 전략 구성(DSL 문장) · 전략 코드(JSON)
/strategies/:id/edit     같은 조회로 초안을 만들어 StrategyEditor 를 수정 모드로. 저장은 새 전략 생성
삭제                     ConfirmDialog → DELETE /api/strategies/:id → 목록
```

공개·복제 버튼은 백엔드가 없어 비활성이다. 백테스트 실행은 `state.strategyId`(`src/strategy/backtestEntry.ts`)를 들고 `/backtest`로 간다.

## 백엔드 호출

```
frontend ──/api──> backend  :8080   랭킹 · 체결 · 전략 · 인증 · 모의투자 · 백테스팅
```

**dev 서버는 프록시로 요청을 넘긴다.** 같은 오리진에서 받으므로 개발 중에는 CORS가 생기지 않는다. 프록시 대상 주소는 `.env`로 바꿀 수 있다.

```
/api/ranking  →  http://localhost:8080/ranking
/api/orders   →  http://localhost:8080/orders
```

Kafka · Redis · DB에 직접 접근하지 않는다. 모든 데이터는 API를 통해서만 받는다.

## 종목별 실시간 차트

- `/chart`에서 등록된 Upbit KRW 거래지원 종목을 선택한다. `GET /api/assets`의 `assetId`는 봉 조회, `marketCode`는 구독에 사용한다. 수집 중이 아니거나 이력이 없는 종목은 빈 상태로 표시된다.
- 같은 오리진 `/ws`에 쿠키 인증으로 접속하고 선택 종목의 `/topic/markets/{marketCode}/trades`, `/topic/markets/{marketCode}/candles/1m` 두 토픽을 구독한다.
- 구독 전송 후 `GET /api/assets/{assetId}/candles/1m?limit=1000`으로 확정 봉을 조회한다. 조회 중 진행 봉은 시간별 최신 값으로 버퍼링해 합친다. 확정된 시간은 진행 봉으로 덮어쓰지 않는다.
- 진행 봉은 차트를 갱신하고 체결은 현재가와 최근 30건에 별도로 표시된다. 시세가 오기 전 현재가는 `—`이며, 연결이 끊겼을 때 남아 있는 값은 마지막 수신값이다.
- 연결 종료를 감지하면 3초 간격으로 재접속한다. 재접속 후 구독·확정 봉 조회를 다시 시작한다. 종목 전환·로그아웃으로 화면을 떠나면 연결과 구독을 종료한다.
- Simple broker는 구독 receipt와 heartbeat를 제공하지 않는다. 구독과 REST의 원자적 스냅샷, 체결 재전송, 아직 도착하지 않은 진행 봉의 복구는 보장하지 않는다. 현재가·진행 봉은 다음 이벤트를 기다린다.
- 가격·수량은 문자열로 표시하고 64비트 순번도 문자열로 보존한다. 차트 좌표만 숫자로 변환한다. 차트는 TradingView Lightweight Charts™를 사용하며 출처 링크를 표시한다.
- 지표·호가·다른 시간봉·과거 페이지네이션은 범위 밖이다.

Vite와 운영 `infra/nginx/j15a107.p.ssafy.io.conf`는 `/api/assets`의 접두사를 유지하고 `/ws` Upgrade를 전달한다. 기존 나머지 REST 프록시 규칙은 그대로다. 운영 Nginx 파일 변경은 배포 시 적용해야 한다.

## 실행 명령

```bash
cp .env.example .env      # 기본값 그대로면 생략 가능
npm install
npm run dev               # :5173
npm run build             # tsc -b && vite build → dist/
npm run lint
npm test                  # Node 24 내장 테스트 (모듈 mock은 실험적 API)
```

## 다음 단계

- [x] 1분봉 차트와 실시간 체결 연동 (Lightweight Charts + STOMP)
- [x] 라우터 도입
- [x] 상태관리 도입 (서버 상태만 TanStack Query, 전역 상태 라이브러리 없음)
- [x] API 클라이언트 레이어
