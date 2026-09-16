# frontend — React / TypeScript

React 19, TypeScript 6, Vite 8 기반 사용자 UI다. 이 디렉터리의 작업에는 루트 `CLAUDE.md`와 아래 규칙을 적용한다.

## 현재 상태

- 화면 목업의 디자인 시스템은 `src/styles`로 옮겨져 있고 실제 서비스 화면을 구현 중이다.
- 화면 기획과 목업은 `../docs/기획/박민규/화면 설계/v1/README.md`를 참고한다.
- 라우터는 `react-router`, 서버 상태는 `@tanstack/react-query`를 쓴다. 전역 상태 라이브러리는 두지 않는다. 차트는 `lightweight-charts`, STOMP는 `@stomp/stompjs`를 쓴다. 별도 테스트 프레임워크 없이 Node 24 내장 테스트 러너를 사용한다.

## 디자인 시스템

- 색·반경·그림자 변수는 `src/styles/tokens.css`에 두고 컴포넌트 클래스는 `src/styles/base.css`에 둔다.
- 컴포넌트에 색상 리터럴을 쓰지 않고 CSS 변수를 참조한다.
- 색 의미 계약을 지킨다. `--up`은 상승·매수, `--down`은 하락·매도, `--crit`은 오류다. 빨강이 상승에 배정돼 있으므로 오류에 빨강을 쓰지 않는다.
- 테마는 문서 루트의 `data-theme` 속성으로만 바꾸고 컴포넌트가 테마를 분기하지 않는다.
- 폰트는 `IBM Plex Sans KR`과 `IBM Plex Mono`를 쓰고 숫자에는 `.num` 또는 `.mono`로 고정폭을 적용한다. 브랜드마크만 `Gothic A1`을 쓴다.
- 목업은 내비게이션을 `button`으로 그렸지만 화면 이동은 `a`로 옮긴다. 그 차이를 메우는 규칙은 `src/styles/overrides.css`에만 두고 `base.css`는 원본과 대조할 수 있게 유지한다.
- 목업에 없는 상태(예: 오류 콜아웃)가 필요하면 목업 클래스의 변형으로 `src/styles/extensions.css`에 추가한다. 새 컴포넌트 계층을 만들지 않는다.
- 설계 폭은 1440px이고 960px 이하에서 사이드바가 접힌다.

## 라우팅

- 경로 문자열은 `src/app/routes.ts`에만 둔다. 컴포넌트는 `routes`와 `to`를 가져다 쓰고 `'/strategies/…'` 같은 문자열을 직접 쓰지 않는다.
- 화면 컴포넌트는 `src/pages`에 두고 목업 번호와 1:1로 대응한다. 같은 화면의 상태 변형(빈 목록, 오류, 모달)은 별도 페이지로 나누지 않는다.
- 로그인은 사이드바가 없는 화면이라 공통 레이아웃 라우트(`AppShell`) 바깥에 둔다.
- 상단바 제목은 라우트 정의의 `handle.title`로 정하고 페이지 컴포넌트가 제목을 그리지 않는다.
- 사이드바 항목은 `src/app/navigation.tsx`에만 추가한다.
- 모달은 네이티브 `dialog`를 `showModal()`로 열고 겉모습만 목업의 `.mbd`·`.modal`을 쓴다. 포커스 트랩, Esc 닫기, 바깥 요소 비활성화는 브라우저에 맡기고 직접 구현하지 않는다. 되돌릴 수 없는 동작의 확인은 `src/components/ConfirmDialog`를 쓴다.
- 드롭다운 메뉴는 열렸을 때만 렌더링하고 바깥 `pointerdown`과 Esc 로 닫으며, 닫힐 때 여는 버튼으로 포커스를 되돌린다.
- 정의되지 않은 경로는 `NotFoundPage`가 받는다.

## API 호출과 인증 상태

- 모든 API 호출은 `src/api/client.ts`의 `request`를 지난다. 컴포넌트가 `fetch`를 직접 부르지 않는다.
- 실패는 `ApiError` 하나로 던진다. 응답 본문은 backend/api 의 `ApiErrorResponse`와 같다.
- 서버 데이터는 TanStack Query 훅으로 읽는다. 훅은 `src/{feature}/queries.ts`에 두고 쿼리 키는 그 파일에서만 정한다.
- 토큰은 `httpOnly` 쿠키라 프론트가 읽거나 저장하지 않는다. 로그인 여부의 유일한 근거는 `useCurrentMember`(`GET /members/me`) 결과다.
- 인증이 필요한 화면은 `RequireAuth` 아래에, 로그인 화면은 `GuestOnly` 아래에 둔다.
- API 응답 타입(`XxxResponse`)과 화면 모델은 `src/{feature}/types.ts`에서 분리하고 변환 함수를 거친다.
- 백테스트 실행 조건 편집과 검증은 `src/backtest/runForm.ts` 의 순수 함수에만 두고 화면은 그 결과를 표시한다. 실행 식별자는 주소의 `run` 파라미터가 소유하며 상태 폴링은 `queries.ts` 가 맡는다. 실행 이력 선별은 `src/backtest/history.ts` 에 둔다.
- 백테스트 기간 경계는 `src/backtest/periodBounds.ts` 가 정한다. 워밍업 봉 수 계산은 백엔드 `BacktestWarmupCandleCountResolver` 와 같은 규칙이라 한쪽이 바뀌면 다른 쪽도 바꾼다. 하한을 계산에 쓰는 곳은 프리셋, 시작일 검증, 기본 기간 셋뿐이고 컴포넌트가 날짜를 직접 계산하지 않는다.
- 캔들 보유 구간은 `GET /candles/coverage` 하나로 받는다. 종목별 최근 봉을 모아 추정하지 않는다. 봉 조회는 1,000개가 상한이라 과거가 쌓일수록 시작일이 잘리고, 화면이 DB 에 있는 구간을 못 쓰게 된다.
- 모의투자 시작 조건은 `src/simulation/startForm.ts`, 보유 종목 평가는 `src/simulation/positions.ts` 의 순수 함수에만 둔다. 현재가가 없는 값은 비우고 추정하지 않는다. 종목 이름은 `assetLookup.ts` 로 맞추고 컴포넌트가 `/assets` 를 따로 부르지 않는다.
- 전략 DSL 계약 타입은 `src/strategy/types.ts`, 열거형 문구·단위는 `src/strategy/labels.ts`, 빌더 편집 동작은 `src/strategy/draft.ts`의 순수 함수에만 둔다. 컴포넌트가 DSL 을 직접 조립하지 않는다.
- 대회 응답 타입은 `src/contest/types.ts`, 상태·장세·실격 문구와 표기는 `src/contest/labels.ts`, 제출 실패 문구는 `src/contest/submitErrorMessage.ts` 에만 둔다. 채점 규칙 숫자는 회차 응답이 준 값만 쓰고 화면이 가중치나 실격선을 다시 적어 두지 않는다. 결과·데이터셋 조회는 종료 전에 부르면 409 라 `useContestProgress` 가 알린 상태로만 켠다.
- 내 제출 성적은 `src/contest/ContestMyScorePanel.tsx` 하나로 대회 상세와 결과 화면이 같이 쓴다. 점수가 비어 있으면 채점 중이라 진행률을 보여 주고, 마감 전 순위에는 잠정이라고 적는다.
- 랭킹 표의 조회 조건은 `src/contest/rankingQuery.ts` 의 순수 함수에만 둔다. 정렬·국면·검색을 바꾸면 결과 수가 달라지므로 첫 페이지로 되돌리고, 조건은 서버에 넘겨 화면이 거르지 않는다. 내 성적 행은 `GET /contests/{roundId}/results/me` 로 따로 받아 표 아래에 고정한다. 페이지를 넘겨도 보여야 한다.
- 후보군 종목 수는 `market/universe.ts` 의 `useUniverseSize()` 하나로 읽는다. 서버가 준 `/assets` 길이가 곧 후보군 크기라 화면이 숫자를 따로 적어 두지 않는다.
- 알림이 어디로 가는지는 화면이 정한다. 서버는 종류와 `targetId` 만 주므로 경로 대응은 `pages/NotificationsPage.tsx` 의 `targetRoute` 하나에 둔다. 시각 표기와 아이콘·색은 `notification/labels.ts` 가 맡는다.
- 알림 색도 색 계약을 따른다. 실패는 `--crit` 을 쓰는 `alert`, 완료는 `--good` 을 쓰는 `done` 이다.
- 전략 공개는 남에게 보이기 시작하는 동작이라 ConfirmDialog 로 한 번 묻는다. 되돌리는 비공개는 묻지 않는다.
- 오류 코드는 `src/api/errorCodes.ts`의 상수만 쓰고 `'AUTH-401-…'` 문자열을 화면 코드에 직접 쓰지 않는다.
- 401 은 화면이 처리하지 않는다. `QueryClient` 캐시의 `onError`가 회원 쿼리를 "로그인 안 됨"으로 바꾸고 `RequireAuth`가 이유를 붙여 로그인 화면으로 보낸다.
- 로딩·빈·오류 상태는 `src/components`의 `LoadingState`, `EmptyState`, `ErrorState`를 쓴다. 화면이 같은 구조를 다시 그리지 않는다.

## 경계

- 차트 책임은 `src/market`의 API·queries, STOMP 연결(선택 종목은 `stream.ts`, 여러 종목 현재가는 `priceStream.ts`), 데이터 검증·병합(`model.ts`), 표시값 계산(`ticker.ts`), 차트 렌더러로 나눈다. `ChartPage`는 종목 선택과 표시만 한다.
- 차트 화면은 목업 04 의 클래스(`.chart-shell` `.coin-list` `.coin-row` `.tick-stats` `.tfs` `.inds` `.chart-legend` `.tr-head` `.trr`)를 쓴다. 이미 `base.css`에 있으므로 같은 목적의 클래스를 새로 만들지 않는다.
- 보조지표 계산은 `src/market/indicators.ts` 의 순수 함수에만 두고 차트는 그린 결과만 받는다. 값은 한 번 계산해 범례와 차트가 함께 쓴다. 기간이 채워지기 전 구간은 값을 만들지 않는다.
- `MinuteCandleChart` 는 차트, 모의투자 실시간, 전략 상세가 함께 쓴다. 크기는 화면마다 다르므로 `#candle` 높이 규칙을 `extensions.css` 에 두고 컴포넌트는 크기를 정하지 않는다.- 체결 마커는 표시 전용이다. 종목 선택과 좌표 변환은 `backtest/tradeMarkers.ts` 의 순수 함수에 두고 차트는 받은 것만 찍는다. 마커가 있으면 그 구간에 화면을 맞춘다.
- 화면 표시용 지표와 전략이 쓰는 팩터를 섞지 않는다. 전자는 프론트가 계산하고 후자는 서버가 계산한 값을 그대로 받는다. 둘이 어긋나면 사용자가 본 숫자와 전략 판단이 달라진다.
- 팩터 값은 `GET /assets/{assetId}/factors` 로 받아 `src/market/factors.ts` 가 표기만 만든다. 기간은 서버가 정하므로 화면에 기간을 적어 두지 않는다. 단위와 소수 자리는 `strategy/labels.ts` 의 `factorMeta` 하나로 빌더와 맞춘다.
- 가격·수량과 int64 체결 순번은 문자열로 보존하며 차트 좌표만 number로 변환한다. 진행 봉은 확정 봉을 덮어쓰지 않고 최대 1,000개, 체결은 최대 30건만 유지한다.
- 지원 간격은 `market/model.ts`의 1·2·5·15·60·240·1440분으로 제한한다. 조회·STOMP 목적지에 동일 간격을 사용하며 UTC 버킷을 검증한다. 1440분봉은 KST 09:00부터다.
- 선택 종목·간격 화면이 연결 하나와 구독 두 개를 소유한다. 종목·간격 전환·언마운트 때 종료하며, 재연결 후 구독을 전송하고 확정 봉을 다시 조회한다. 쿼리 키에 종목·간격·연결 세대를 포함해 조회 결과와 진행 봉 버퍼를 격리한다. 확정/보정 결과는 1분마다 재조회한다.

- 브라우저는 `/api`를 통해 Spring API만 호출한다.
- AI 서버, Upbit, PostgreSQL, Redis, Kafka에 직접 연결하지 않는다.
- 개발 환경의 API 대상은 Vite 프록시와 환경변수로 설정하고 주소를 컴포넌트에 하드코딩하지 않는다.
- 브라우저에 노출되면 안 되는 키와 비밀값을 `VITE_` 환경변수에 넣지 않는다.

## 구현 규칙

- TypeScript strict 설정을 유지하고 불가피한 이유 없이 `any`를 사용하지 않는다.
- React 함수 컴포넌트와 명시적인 Props 타입을 사용한다.
- API 응답 타입, 화면 상태, 표시용 모델을 구분한다.
- 로딩·빈 상태·오류·성공 상태를 화면 요구사항에 맞게 함께 처리한다.
- 접근 가능한 HTML 요소와 키보드 조작을 우선하고 클릭용 `div`를 만들지 않는다.
- 컴포넌트에 API 주소, 에러 코드, 타임프레임 같은 문자열을 반복해서 하드코딩하지 않는다. 오류 코드와 사용자 문구의 대응은 기능별 `*ErrorMessage.ts`(`src/auth/loginErrorMessage.ts`, `src/strategy/translateErrorMessage.ts`)에만 둔다.
- 백테스트 계산이나 거래 판단 같은 서버 책임을 프론트에서 재구현하지 않는다.
- `dist/`, 캐시, 생성물을 직접 수정하거나 커밋하지 않는다.

## 스타일과 구조

- 기존 화면 스타일과 명명 방식을 우선한다.
- 실제 기능이 생기기 전부터 범용 `common`, `utils`, 전역 상태 계층을 만들지 않는다.
- 재사용이 확인된 UI만 공통 컴포넌트로 추출한다.
- 목업 HTML을 그대로 복사하지 않고 React 컴포넌트와 접근성 있는 마크업으로 옮긴다. 스타일은 이관된 `src/styles`를 재사용하고 다시 작성하지 않는다.

## 검증

```bash
npm run lint
npm run build
npm test
```

`npm test`는 Node 24의 내장 테스트·모듈 mock으로 데이터 병합과 STOMP 연결 수명주기를 검증한다. 추가 테스트 프레임워크는 도입하지 않는다.
