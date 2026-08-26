# frontend

> 트레이딩 차트 UI. **환경만 구성됨 — 화면 구현 없음.**

React 19 + TypeScript + Vite.

## 스택

| 항목 | 버전 |
|---|---|
| React | 19 |
| TypeScript | 6 |
| Vite | 8 |
| 린터 | oxlint (템플릿 기본) |

**차트 라이브러리 · 라우터 · 상태관리는 아직 넣지 않았다.** 화면을 만들 때 결정할 일이다.

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
