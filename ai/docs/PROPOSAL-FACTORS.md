# Factor 18개 제안 — ta4j 기준

대상: 전략팀 · 백엔드(백테스트 엔진)
작성일: 2026-08-31
전제: **이 문서는 정의처가 아니다.** 확정 필드명과 허용값은 `app/core/schema.py`(AI 서버)와 `src/types/strategy.ts`(프론트)에만 적는다. 여기는 "무엇을 넣을지"의 제안과 근거다. 전략 도메인의 결정권은 전략팀에 있다.

관련: [REVIEW-STRATEGY-MVP.md](REVIEW-STRATEGY-MVP.md) — Factor 6개 확정 경위와 미결 항목

---

## 왜 18개인가

MVP Factor는 6개다. 발표에서 보여줄 전략 폭을 넓히려면 늘려야 하는데, **늘리는 비용이 어디에 걸리는지가 층마다 다르다.**

| 제약 | 상한을 어디서 만드나 |
|---|---|
| 구현 | 걸리지 않는다. ta4j에 130개 이상 있고, 레지스트리 방식이면 팩터 추가가 한 줄이다 |
| **축 중복** | RSI · ROC · CCI · Williams %R은 사실상 같은 축이다. 4개를 넣어도 전략이 4배 다양해지지 않는다 |
| **LLM 변환 정확도** | 열거형 값이 늘수록 오답 자리가 는다. 실제로 쓸 것만 넣는다 |
| 화면 | 드롭다운에 40개가 뜨면 사용자가 못 고른다. 한국어 라벨과 설명도 개당 손이 간다 |
| 설명 책임 | 심사에서 "이건 뭐냐"를 물었을 때 답할 수 있는 개수여야 한다 |

ta4j 130개를 걸러 보면 이렇게 남는다.

| 부류 | 대략 | 랭킹형에 쓸 수 있나 |
|---|---|---|
| 이동평균 변종 (SMA · EMA · WMA · HMA · KAMA · ZLEMA · DEMA · TEMA …) | 20 | 전부 같은 축. 이격률 하나로 덮인다 |
| 캔들 패턴 (`indicators/candles/`) | 10 | 불리언이라 정렬이 성립하지 않는다. 필터로만 |
| 이치모쿠 · 피봇 · 돈치안 | 15 | 가격 레벨. 정규화하면 결국 이격률이나 위치가 된다 |
| 헬퍼 (`ClosePriceIndicator`, `VolumeIndicator` …) | 30 | 지표가 아니라 부품 |
| **축이 실제로 다른 지표** | **30~40** | 후보 풀 |

여기서 상관이 높은 것을 걷어내면 18개쯤이 남는다. **18은 아껴 고른 수가 아니라 걸러내면 나오는 수다.**

---

## 팩터 18개

정규화 열은 횡단면 비교를 성립시키는 방식이다(정의는 아래 "정규화 3종"). 기본 방향과 기본 기간은 사용자가 안 말했을 때 서버가 채우는 값이다.

### 모멘텀 축

| name | 라벨 | 정규화 | 기본 방향 | 기본 기간 | ta4j |
|---|---|---|---|---|---|
| `RETURN` | 수익률 | RATIO | DESC | 20 | `ReturnIndicator` |
| `RSI` | RSI | BOUNDED | ASC | 14 | `RSIIndicator` |
| `ROC` | 변화율 | RATIO | DESC | 20 | `ROCIndicator` |
| `MFI` | 자금흐름지수 | BOUNDED | ASC | 14 | `volume/MoneyFlowIndexIndicator` |
| `STOCH_K` | 스토캐스틱 %K | BOUNDED | ASC | 14 | `StochasticOscillatorKIndicator` |

### 추세 축

| name | 라벨 | 정규화 | 기본 방향 | 기본 기간 | ta4j |
|---|---|---|---|---|---|
| `DISPARITY_SMA` | 이동평균 이격률 | RATIO | DESC | 20 | `DistanceFromMAIndicator` |
| `DISPARITY_EMA` | 지수이평 이격률 | RATIO | DESC | 20 | `DistanceFromMAIndicator` (EMA 주입) |
| `MACD_HIST` | MACD 히스토그램 | RATIO | DESC | 12/26/9 | `MACDIndicator` + 종가로 나눔 |
| `ADX` | 추세강도 | BOUNDED | DESC | 14 | `adx/ADXIndicator` |
| `AROON_OSC` | 아룬 오실레이터 | BOUNDED | DESC | 25 | `aroon/AroonOscillatorIndicator` |

### 위험 축

| name | 라벨 | 정규화 | 기본 방향 | 기본 기간 | ta4j |
|---|---|---|---|---|---|
| `VOLATILITY` | 변동성 | RATIO | ASC | 20 | `statistics/StandardDeviationIndicator` + `helpers/ReturnIndicator` |
| `DRAWDOWN` | 고점 대비 낙폭 | RATIO | DESC | 60 | `helpers/HighestValueIndicator` + `helpers/CombineIndicator` |
| `ATR_RATIO` | ATR 비율 | RATIO | ASC | 14 | `ATRIndicator` ÷ 종가 |
| `BB_WIDTH` | 볼린저 밴드폭 | RATIO | ASC | 20 | `bollinger/BollingerBandWidthIndicator` |

### 거래 축

| name | 라벨 | 정규화 | 기본 방향 | 기본 기간 | ta4j |
|---|---|---|---|---|---|
| `VOLUME_RATIO` | 거래량 비율 | RATIO | DESC | 20 | `helpers/VolumeIndicator` + `averages/SMAIndicator` |
| `TRADING_VALUE` | 거래대금 | ZSCORE | DESC | 1 | `helpers/AmountIndicator` |
| `OBV_SLOPE` | OBV 기울기 | ZSCORE | DESC | 20 | `volume/OnBalanceVolumeIndicator` + `statistics/SimpleLinearRegressionIndicator` |

### 위치 축

| name | 라벨 | 정규화 | 기본 방향 | 기본 기간 | ta4j |
|---|---|---|---|---|---|
| `BB_PERCENT_B` | 밴드 내 위치 | RATIO | ASC | 20 | `bollinger/PercentBIndicator` |
| `CCI` | CCI | RATIO | ASC | 20 | `CCIIndicator` |

**13개는 ta4j 클래스를 그대로 부른다.** 나머지 5개(`MACD_HIST` · `VOLATILITY` · `DRAWDOWN` · `ATR_RATIO` · `OBV_SLOPE`)도 ta4j 부품 두 개를 조합하는 수준이고 개당 몇 줄이다. 클래스명은 2026-08-31 기준 `ta4j/ta4j` master에서 확인했다.

---

## 조합이 필요한 5개

```
MACD_HIST   = (MACD - MACD의 9봉 EMA) / 종가
VOLATILITY  = ReturnIndicator의 N봉 표준편차          // 연율화하지 않음
DRAWDOWN    = 종가 / N봉 최고가 - 1                    // 항상 0 이하
ATR_RATIO   = ATR(N) / 종가
OBV_SLOPE   = OBV의 N봉 선형회귀 기울기 / |OBV 평균|
```

`MACD_HIST`와 `ATR_RATIO`를 종가로 나누는 이유는 하나다. **원시값이 가격 스케일이라 코인 간 비교가 성립하지 않는다.** BTC의 ATR 3,200,000과 DOGE의 ATR 12를 나란히 정렬하면 항상 BTC가 이긴다.

`DRAWDOWN`의 부호는 음수로 잡았다. 아직 미결 항목이라([REVIEW-STRATEGY-MVP.md C-4](REVIEW-STRATEGY-MVP.md)) 확정되면 따른다.

---

## 정규화 3종

리밸런싱 시점마다 **그날 살아있는 종목들끼리** 다시 계산한다. 전 기간 통계를 쓰면 미래를 보는 것이 된다.

| 방식 | 처리 | 쓰는 곳 |
|---|---|---|
| `BOUNDED` | 그대로 쓴다 (이미 0~100 등 고정 범위) | RSI · MFI · STOCH_K · ADX · AROON_OSC |
| `RATIO` | 그대로 쓴다 (이미 비율이라 스케일 없음) | 수익률 · 이격률 · 변동성 · 낙폭 · 밴드폭 |
| `ZSCORE` | 그 시점 종목들의 평균·표준편차로 표준화 | 거래대금 · OBV 기울기 등 절대값 지표 |

**`ZSCORE`가 있으면 ta4j 지표를 사실상 아무거나 열 수 있다.** 절대값이어도 그 시점 종목끼리 표준화하니 비교가 성립한다. 팩터를 나중에 늘릴 때의 문은 여기다.

### 함정 하나

ta4j에 `statistics/ZScoreIndicator`가 있는데 **이것은 한 종목의 시계열 z-score다.** 우리가 필요한 횡단면 z-score와 이름만 같고 다른 것이다. 그대로 쓰면 정렬이 조용히 틀린다. 횡단면 정규화는 엔진 쪽에서 직접 구현한다.

---

## 섞으면 안 되는 조합

composite ranking에서 상관이 높은 팩터를 함께 넣으면 **같은 축에 가중치를 두 번 주는 것**이라 분산이 되지 않는다.

| 조합 | 문제 |
|---|---|
| `RETURN` + `ROC` | 계산이 거의 같다. 하나만 노출하는 것도 방법이다 |
| `RETURN` + `DISPARITY_SMA` (같은 기간) | 20일 수익률이 높으면 20일선 위에 있다 |
| `RSI` + `STOCH_K` + `CCI` + `MFI` | 전부 과매수·과매도 축. 넷 다 넣어도 하나 넣은 것과 비슷하다 |
| `VOLATILITY` + `ATR_RATIO` + `BB_WIDTH` | 셋 다 변동성 측정치. 계산법만 다르다 |
| `VOLATILITY` + `DRAWDOWN` | 같은 위험 축. 코인에서 상관이 높다 |
| `RETURN` + `DRAWDOWN` | **부호가 반대라 서로 상쇄한다.** 많이 오른 종목이 낙폭도 크다 |
| `TRADING_VALUE` + universe 조건 | 후보군을 이미 거래대금 상위로 뽑았으면 그 안에서 또 거래대금으로 줄 세우는 셈이다 |

**축이 다른 것끼리 섞을 때만 composite가 의미를 갖는다.** 검증기에서 같은 축 두 개를 경고로 잡는 것을 제안한다 — 거부는 아니다. 사용자가 원할 수도 있다.

상관 계수는 추정이 아니라 측정할 것이다. 업비트 공개 캔들로 18개 팩터의 상관행렬을 뽑으면 위 표가 숫자로 바뀐다. 발표에서 "이 조합은 막았습니다, 근거는 상관 0.87"이 되는 자리다.

---

## composite ranking

팩터 개수를 늘리는 것보다 **조합을 여는 쪽이 전략 폭을 크게 넓힌다.** 18개를 단일 정렬로만 쓰면 전략이 18가지지만, 두 개 조합을 허용하면 축을 넘나드는 조합만 세어도 백 가지가 넘는다.

```json
"ranking": {
  "composite": [
    { "factor": "RETURN",     "period": 20, "weight": 0.6, "direction": "DESC" },
    { "factor": "VOLATILITY", "period": 20, "weight": 0.4, "direction": "ASC"  }
  ]
}
```

각 팩터를 정규화한 뒤 가중합해서 정렬한다. 엔진 쪽 추가 작업은 정규화 함수와 가중합뿐이다. 자연어 쪽에서도 자연스럽다 — "수익률 높으면서 변동성 낮은 걸로 골라줘"가 그대로 들어간다.

구성 개수 상한은 2~3개를 제안한다. 넘어가면 사용자도 결과를 해석하지 못한다.

---

## 결정이 필요한 것

| # | 항목 | 막히는 것 | 제안 |
|---|---|---|---|
| 1 | 18개를 다 열 것인가, 단계적으로 열 것인가 | 열거형 크기 | 6개로 배선 완성 후 12개 추가 |
| 2 | `MACD_HIST`의 파라미터 3개 | 레지스트리 구조 | 12/26/9 고정. 사용자에게 노출하지 않는다 |
| 3 | composite 도입 여부와 구성 개수 상한 | 스키마 · 엔진 | 도입, 최대 2개 |
| 4 | 같은 축 중복을 경고로 잡을지 | 검증 규칙 | 경고. 거부하지 않는다 |
| 5 | `DRAWDOWN` 부호 | 정렬 방향 | 음수 (기존 제안 유지) |
| 6 | `RETURN`과 `ROC` 둘 다 노출할지 | 열거형 | 하나만. `RETURN` 쪽이 말이 통한다 |

2번은 레지스트리 설계에 걸린다. 나머지 17개는 파라미터가 기간 하나인데 `MACD_HIST`만 셋이다. 고정하지 않으면 레지스트리가 파라미터 목록을 받는 구조로 커진다.

---

## 도입 순서

1. **6개로 배선을 끝낸다.** ta4j 연결, 팩터 레지스트리, 정규화 3종, 랭킹·리밸런싱 루프. 도는 것을 확인한다
2. **12개를 얹는다.** 레지스트리가 서 있으면 코드 리뷰가 필요 없는 작업이다. 반나절
3. **composite를 연다.** 스키마와 엔진 양쪽 합의가 필요하다
4. 상관행렬을 측정해 "섞으면 안 되는 조합" 표를 숫자로 바꾼다

배선이 서기 전에 팩터를 늘리면 12개 추가가 12번의 디버깅이 된다. 순서를 지키는 편이 빠르다.
