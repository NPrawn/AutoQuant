# frontend

> 트레이딩 차트 UI. **프레임워크 미정 — 플레이스홀더.**

## 결정 대기

데이터 흐름도에 **Vue와 React가 모두 표기**되어 있어 아직 확정되지 않았다. 결정 후 이 디렉토리를 초기화한다.

| 후보 | 참고 |
|---|---|
| React | TradingView Lightweight Charts 예제·생태계가 가장 두텁다 |
| Vue | 팀 숙련도가 높다면 유리. 주요 차트 라이브러리는 프레임워크 무관 |

## 책임 (예정)

- 실시간 시세 차트 (TradingChart)
- 랭킹 · 체결 내역 표시
- 모의투자 주문 UI, 백테스팅 결과 시각화

## 호출 대상

```
frontend ──HTTP──> apps/api-market   :8080   랭킹 · 체결 · 전략 · 인증
         ──HTTP──> apps/api-trading  :8081   모의투자 · 백테스팅
```

Kafka · Redis · DB에 직접 접근하지 않는다. 모든 데이터는 두 API를 통해서만 받는다.

## 다음 단계

- [ ] 프레임워크 확정 (React / Vue)
- [ ] 프로젝트 초기화 (Vite + TypeScript)
- [ ] 차트 라이브러리 선정
