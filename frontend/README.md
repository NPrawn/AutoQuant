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

**차트 라이브러리 · 라우터 · 상태관리는 아직 넣지 않았다.** 화면을 만들 때 결정할 일이다.

## 디자인 시스템

화면 목업 29개에 같은 스타일시트가 내장돼 있어 한 벌만 `src/styles`로 옮겼다. 화면을 만들 때는 이 클래스를 재사용하고 스타일을 다시 쓰지 않는다.

| 파일 | 내용 |
|---|---|
| `src/styles/tokens.css` | 색·반경·그림자 CSS 변수, 다크·라이트 두 팔레트 |
| `src/styles/base.css` | 리셋, 공통 클래스, 화면별 컴포넌트 스타일 |
| `src/styles/theme.ts` | `data-theme` 속성 전환과 선택 저장 |

**색에 의미가 배정돼 있다.** 한국 시장 관례를 따라 빨강(`--up`)이 상승이므로 오류에는 주황(`--crit`)을 쓴다. 파랑(`--down`)은 하락, 초록(`--good`)은 정상, 보라(`--dd`)는 낙폭이다. 흰색은 선택·포커스 같은 UI 상태를 뜻하고 금융 의미를 담지 않는다.

테마는 문서 루트의 `data-theme` 속성으로만 바뀐다. 첫 페인트에서 다크 화면이 번쩍이지 않도록 `index.html`의 인라인 스크립트가 마운트 전에 복원한다.

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
- [ ] 라우터 · 상태관리 도입 여부 결정
- [ ] API 클라이언트 레이어
