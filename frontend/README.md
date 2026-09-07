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

**차트 라이브러리는 아직 넣지 않았다.** 차트 화면을 만들 때 결정할 일이다.

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
├── auth/                 인증 API, 회원 모델, useCurrentMember, RequireAuth, GuestOnly
├── pages/                화면, 목업 번호와 1:1
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
```

토큰은 `httpOnly` 쿠키라 JS 가 읽지 못한다. 그래서 `/members/me` 응답이 로그인 상태의 유일한 근거이고, 프론트에 토큰 저장 코드가 없다. 쿠키는 `localhost:8080`이 심지만 브라우저 쿠키는 포트를 구분하지 않아 `localhost:5173` 요청에도 실린다.

콜백이 실패하면 백엔드가 `/login?error={코드}`로 보내고, 코드별 문구는 `src/auth/loginErrorMessage.ts`에 있다.

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

## 실행

```bash
cp .env.example .env      # 기본값 그대로면 생략 가능
npm install
npm run dev               # :5173
npm run build             # tsc -b && vite build → dist/
npm run lint
```

## 다음 단계

- [ ] 차트 라이브러리 선정 (TradingView Lightweight Charts 등)
- [x] 라우터 도입
- [x] 상태관리 도입 (서버 상태만 TanStack Query, 전역 상태 라이브러리 없음)
- [x] API 클라이언트 레이어
