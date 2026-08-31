# Strategy 도메인 명세

## 1. 개요

Strategy는 사용자가 생성한 **자동매매 전략의 설정을 저장하는 도메인**이다.

사용자는 기술적 지표를 기반으로 투자 조건을 구성하고, 조건을 통과한 투자 대상에 대해 랭킹 및 Top N 선정을 수행하도록 전략을 설정한다.

생성된 Strategy는 이후 다음 기능에서 공통으로 사용한다.

```text
Strategy
├── Backtest
└── Paper Trading
```

Strategy는 생성 시점의 설정을 보존하기 위해 **Immutable Snapshot**으로 관리한다.

---

# 2. ERD

## 2.1 Strategy 테이블

```text
STRATEGY
────────────────────────────────
strategy_id      BIGINT PK
user_id          BIGINT FK NOT NULL
name             VARCHAR(100) NOT NULL
description      TEXT
strategy_dsl     JSONB NOT NULL
created_at       TIMESTAMPTZ NOT NULL
deleted_at       TIMESTAMPTZ
```

## 2.2 관계

```text
USER
 │
 │ 1:N
 ▼
STRATEGY
```

한 사용자는 여러 개의 Strategy를 생성할 수 있다.

Strategy 간에는 별도의 관계를 두지 않는다.

따라서 다음과 같은 버전 관리 필드 및 테이블은 사용하지 않는다.

```text
version
parent_strategy_id
strategy_group_id
StrategyVersion
```

---

# 3. Strategy 생명주기 정책

## 3.1 Immutable Strategy

Strategy는 생성 이후 다음 데이터를 수정하지 않는다.

```text
name
description
strategyDsl
```

사용자가 기존 Strategy의 설정을 수정하는 경우에도 기존 Strategy를 UPDATE하지 않는다.

변경된 설정으로 **새로운 Strategy를 생성**한다.

예:

```text
Strategy #12

RSI(14) < 30
Top 5

        ↓ 사용자 수정

Strategy #37

RSI(14) < 25
Top 3
```

`Strategy #12`와 `Strategy #37`은 DB 관점에서 서로 독립적인 Strategy이다.

따라서 Strategy 수정 API는 제공하지 않는다.

```http
PUT   /strategies/{strategyId}   X
PATCH /strategies/{strategyId}   X
```

---

## 3.2 Soft Delete

Strategy 삭제는 실제 Row를 제거하지 않고 `deleted_at`을 기록하는 **Soft Delete** 방식으로 처리한다.

```text
deleted_at == NULL
→ 활성 Strategy

deleted_at != NULL
→ 삭제된 Strategy
```

삭제된 Strategy는 일반적인 목록 및 상세 조회에서 제외한다.

Strategy의 설정 데이터는 immutable이지만 Soft Delete를 위해 `deleted_at`은 변경할 수 있다.

---

# 4. Strategy DSL

전략의 실제 설정은 PostgreSQL의 `JSONB` 타입을 사용하여 `strategy_dsl` 컬럼 하나에 저장한다.

관계형 컬럼과 JSONB의 역할은 다음과 같이 구분한다.

```text
Strategy

├── Relational Data
│   ├── strategyId
│   ├── userId
│   ├── name
│   ├── description
│   ├── createdAt
│   └── deletedAt
│
└── Strategy DSL (JSONB)
    ├── timeframe
    ├── filters
    ├── ranking
    ├── selection
    └── rebalance
```

관계형 컬럼은 Strategy의 **식별, 소유권, 생명주기**를 관리한다.

`strategy_dsl`은 Strategy Engine에서 사용할 **실행 가능한 전략 규칙**을 표현한다.

---

# 5. DSL 전체 구조

Strategy DSL의 기본 구조는 다음과 같다.

```json
{
  "timeframe": "4H",
  "filters": {
    "type": "GROUP",
    "logic": "OR",
    "children": [
      {
        "type": "GROUP",
        "logic": "AND",
        "children": [
          {
            "type": "CONDITION",
            "left": {
              "type": "FACTOR",
              "factor": "RSI",
              "period": 20
            },
            "operator": "LT",
            "right": {
              "type": "FACTOR",
              "factor": "RSI",
              "period": 60
            }
          },
          {
            "type": "CONDITION",
            "left": {
              "type": "FACTOR",
              "factor": "VOLUME_RATIO",
              "period": 20
            },
            "operator": "LT",
            "right": {
              "type": "VALUE",
              "value": 1.2
            }
          }
        ]
      },
      {
        "type": "CONDITION",
        "left": {
          "type": "FACTOR",
          "factor": "RETURN",
          "period": 20
        },
        "operator": "GT",
        "right": {
          "type": "VALUE",
          "value": 5
        }
      }
    ]
  },
  "ranking": {
    "factor": "RETURN",
    "period": 20,
    "direction": "DESC"
  },
  "selection": {
    "topN": 5
  },
  "rebalance": {
    "period": "WEEKLY"
  }
}
```

위 Filter는 다음 조건을 의미한다.

```text
(
    RSI(20) < RSI(60)
    AND
    VOLUME_RATIO(20) < 1.2
)
OR
RETURN(20) > 5
```

---

# 6. Timeframe

`timeframe`은 Strategy에서 Factor를 계산할 때 사용하는 **Candle 단위**를 의미한다.

## 지원 값

| 값 | 의미 |
|---|---|
| `1H` | 1시간봉 |
| `4H` | 4시간봉 |
| `1D` | 일봉 |

예:

```json
{
  "timeframe": "4H"
}
```

Factor의 `period`는 Strategy의 `timeframe`을 기준으로 해석한다.

예를 들어:

```text
timeframe = 4H
RSI(20)
```

이라면 4시간봉 20개를 기준으로 RSI를 계산한다.

---

# 7. Filter

Filter는 전체 투자 대상 중에서 **랭킹 대상이 될 수 있는 종목을 선별하는 조건**이다.

```text
Universe
   ↓
Filter
   ↓
Eligible Assets
```

Filter는 다음 두 종류의 Node로 구성된다.

```text
FilterNode

├── GROUP
└── CONDITION
```

`GROUP` 내부에 다시 `GROUP` 또는 `CONDITION`을 포함할 수 있는 **재귀 구조**를 사용한다.

---

## 7.1 GROUP

여러 개의 FilterNode를 `AND` 또는 `OR`로 결합한다.

```json
{
  "type": "GROUP",
  "logic": "AND",
  "children": [
    ...
  ]
}
```

### 필드

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `type` | Enum | O | `GROUP` |
| `logic` | Enum | O | `AND`, `OR` |
| `children` | FilterNode[] | O | 하위 조건 |

`children`에는 최소 하나 이상의 FilterNode가 존재해야 한다.

---

## 7.2 CONDITION

실제 비교 조건 하나를 표현한다.

```json
{
  "type": "CONDITION",
  "left": {
    "type": "FACTOR",
    "factor": "RSI",
    "period": 14
  },
  "operator": "LT",
  "right": {
    "type": "VALUE",
    "value": 30
  }
}
```

구조는 다음과 같다.

```text
Left Operand
     ↓
  Operator
     ↓
Right Operand
```

따라서 다음과 같은 조건을 표현할 수 있다.

```text
RSI(14) < 30

RETURN(20) > 5

RSI(20) < RSI(60)
```

---

# 8. Logic

GROUP 내부의 여러 FilterNode를 결합하는 논리 연산자이다.

## 지원 값

| 값 | 의미 |
|---|---|
| `AND` | 모든 조건을 만족 |
| `OR` | 하나 이상의 조건을 만족 |

예:

```text
RSI(14) < 30
AND
VOLUME_RATIO(20) > 1.5
```

또는:

```text
RSI(14) < 30
OR
RETURN(20) > 5
```

---

# 9. Operand

CONDITION의 좌변과 우변에 위치하는 값이다.

Operand는 다음 두 종류를 지원한다.

```text
Operand

├── FACTOR
└── VALUE
```

---

## 9.1 FACTOR Operand

기술적 지표 또는 계산된 Factor를 의미한다.

```json
{
  "type": "FACTOR",
  "factor": "RSI",
  "period": 14
}
```

### 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | Enum | `FACTOR` |
| `factor` | FactorType | 사용할 Factor |
| `period` | Integer | Factor 계산 기간 |

`period`는 0보다 커야 한다.

---

## 9.2 VALUE Operand

사용자가 입력한 고정 숫자를 의미한다.

```json
{
  "type": "VALUE",
  "value": 30
}
```

### 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | Enum | `VALUE` |
| `value` | Number | 비교할 상수값 |

따라서:

```text
RSI(14) < 30
```

은 내부적으로:

```text
FACTOR(RSI, 14)
LT
VALUE(30)
```

으로 표현된다.

---

# 10. Factor

MVP에서 다음 Factor를 지원한다.

| Factor | 의미 |
|---|---|
| `RETURN` | N기간 수익률 |
| `RSI` | Relative Strength Index |
| `SMA_DISPARITY` | 현재 가격의 SMA 대비 이격도 |
| `VOLATILITY` | N기간 변동성 |
| `VOLUME_RATIO` | 현재 거래량의 평균 거래량 대비 비율 |
| `DRAWDOWN` | N기간 고점 대비 하락률 |

Factor는 Strategy에 설정된 `timeframe`을 기준으로 계산한다.

예:

```text
timeframe = 4H
RETURN(20)
```

이면 4시간봉을 기준으로 20기간 수익률을 계산한다.

---

# 11. Operator

CONDITION에서 좌변과 우변을 비교하기 위한 연산자이다.

## 지원 값

| Enum | 연산 | 의미 |
|---|---|---|
| `GT` | `>` | 초과 |
| `GTE` | `>=` | 이상 |
| `LT` | `<` | 미만 |
| `LTE` | `<=` | 이하 |

예:

```text
RETURN(20) > 5
```

DSL:

```json
{
  "type": "CONDITION",
  "left": {
    "type": "FACTOR",
    "factor": "RETURN",
    "period": 20
  },
  "operator": "GT",
  "right": {
    "type": "VALUE",
    "value": 5
  }
}
```

---

# 12. Ranking

Filter를 통과한 종목을 특정 Factor를 기준으로 정렬한다.

```text
Eligible Assets
       ↓
    Ranking
       ↓
 Ranked Assets
```

예:

```json
{
  "factor": "RETURN",
  "period": 20,
  "direction": "DESC"
}
```

### 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `factor` | FactorType | Ranking 기준 Factor |
| `period` | Integer | Factor 계산 기간 |
| `direction` | Enum | 정렬 방향 |

## Ranking Direction

| 값 | 의미 |
|---|---|
| `ASC` | Factor 값이 낮은 순 |
| `DESC` | Factor 값이 높은 순 |

예:

```text
RETURN(20) DESC
```

이면 20기간 수익률이 높은 종목부터 정렬한다.

---

# 13. Selection

Ranking 결과에서 실제 투자 대상으로 사용할 최대 종목 수를 지정한다.

```json
{
  "topN": 5
}
```

### 필드

| 필드 | 타입 | 조건 | 설명 |
|---|---|---|---|
| `topN` | Integer | `> 0` | 최대 선택 종목 수 |

`topN`은 **반드시 N개의 종목을 선택한다는 의미가 아니라 최대 N개를 선택한다는 의미**이다.

예:

```text
Filter 통과 = 10개
TopN = 5

→ 5개 선택
```

```text
Filter 통과 = 3개
TopN = 5

→ 3개 선택
```

```text
Filter 통과 = 0개
TopN = 5

→ 0개 선택
```

따라서:

```text
selectedCount = min(filterPassCount, topN)
```

으로 처리한다.

Filter를 통과한 종목이 없다면 새로운 투자 대상을 선정하지 않는다.

---

# 14. Rebalance

Rebalance는 Strategy를 다시 평가하여 Portfolio를 재구성하는 주기이다.

```json
{
  "period": "WEEKLY"
}
```

## 지원 값

| 값 | 의미 |
|---|---|
| `DAILY` | 매일 |
| `WEEKLY` | 매주 |
| `MONTHLY` | 매월 |

Rebalance 시점마다 다음 과정을 다시 수행한다.

```text
Universe
   ↓
Factor 계산
   ↓
Filter
   ↓
Ranking
   ↓
Top N Selection
   ↓
Target Portfolio
```

새로운 Target Portfolio와 기존 Portfolio를 비교한다.

```text
기존 Top N ∩ 새로운 Top N
→ 유지

기존 Top N - 새로운 Top N
→ 매도

새로운 Top N - 기존 Top N
→ 매수
```

Rebalance 시점이라고 해서 반드시 거래가 발생하는 것은 아니다.

기존 Portfolio와 새로운 Target Portfolio가 동일하면 종목 교체 거래는 발생하지 않는다.

---

# 15. Portfolio 구성 정책

MVP에서는 Selection을 통과한 종목에 대해 **동일 비중(Equal Weight)** 방식으로 투자한다.

예:

```text
선택 종목 = 5개

BTC
ETH
XRP
SOL
ADA

→ 각 20%
```

Filter를 통과한 종목이 Top N보다 적은 경우 실제 선택된 종목을 기준으로 동일 비중을 계산한다.

예:

```text
TopN = 5
Filter 통과 = 3개

→ 3개 선택
→ 각 약 33.33%
```

Filter를 통과한 종목이 없다면:

```text
선택 종목 = 0
Cash = 100%
```

으로 처리한다.

---

# 16. Strategy 실행 흐름

Strategy DSL의 전체 실행 흐름은 다음과 같다.

```text
① Universe 구성
        ↓
② Strategy timeframe 기준 Market Data 조회
        ↓
③ Factor 계산
        ↓
④ Filter 평가
        ↓
⑤ Ranking
        ↓
⑥ Top N Selection
        ↓
⑦ 동일 비중 Target Portfolio 구성
        ↓
⑧ 기존 Portfolio와 비교
        ↓
⑨ 주문 생성
        ↓
⑩ 다음 봉 시가 기준 체결
```

Strategy 자체는 위 로직을 직접 실행하지 않는다.

Strategy는 **실행에 필요한 설정을 정의하고 저장하는 역할**만 담당한다.

실제 계산과 실행은 별도의 Engine에서 담당한다.

---

# 17. Validation 명세

Strategy 생성 시 다음 조건을 검증한다.

## Strategy

| 필드 | Validation |
|---|---|
| `name` | 필수 |
| `name` | Blank 불가 |
| `name` | 최대 100자 |
| `strategyDsl` | 필수 |

## Strategy DSL

| 대상 | Validation |
|---|---|
| `timeframe` | 필수 |
| `timeframe` | 지원하는 Timeframe이어야 함 |
| `filters` | 필수 |
| `ranking` | 필수 |
| `selection` | 필수 |
| `rebalance` | 필수 |

## GROUP

| 필드 | Validation |
|---|---|
| `type` | `GROUP` |
| `logic` | 필수 |
| `children` | 필수 |
| `children` | 최소 1개 |

`children` 내부의 모든 FilterNode도 재귀적으로 검증한다.

## CONDITION

| 필드 | Validation |
|---|---|
| `type` | `CONDITION` |
| `left` | 필수 |
| `operator` | 필수 |
| `right` | 필수 |

## FACTOR Operand

| 필드 | Validation |
|---|---|
| `type` | `FACTOR` |
| `factor` | 필수 |
| `period` | 필수 |
| `period` | `> 0` |

## VALUE Operand

| 필드 | Validation |
|---|---|
| `type` | `VALUE` |
| `value` | 필수 |

## Ranking

| 필드 | Validation |
|---|---|
| `factor` | 필수 |
| `period` | 필수 |
| `period` | `> 0` |
| `direction` | 필수 |

## Selection

| 필드 | Validation |
|---|---|
| `topN` | 필수 |
| `topN` | `> 0` |

## Rebalance

| 필드 | Validation |
|---|---|
| `period` | 필수 |

---

# 18. Strategy API 명세

Strategy 도메인에서는 다음 API를 제공한다.

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/strategies` | 새로운 전략 생성 |
| `GET` | `/strategies` | 내 전략 목록 조회 |
| `GET` | `/strategies/{strategyId}` | 전략 상세 조회 |
| `DELETE` | `/strategies/{strategyId}` | 전략 Soft Delete |

프로젝트에 `/api`, `/api/v1` 등의 공통 Prefix가 존재하는 경우 프로젝트 컨벤션을 따른다.

---

## 18.1 전략 생성

```http
POST /strategies
```

새로운 Strategy를 생성한다.

### Request 예시

```json
{
  "name": "상승 모멘텀 전략",
  "description": "RSI와 거래량을 활용한 모멘텀 전략",
  "strategyDsl": {
    "timeframe": "4H",
    "filters": {
      "type": "CONDITION",
      "left": {
        "type": "FACTOR",
        "factor": "RSI",
        "period": 14
      },
      "operator": "LT",
      "right": {
        "type": "VALUE",
        "value": 70
      }
    },
    "ranking": {
      "factor": "RETURN",
      "period": 20,
      "direction": "DESC"
    },
    "selection": {
      "topN": 5
    },
    "rebalance": {
      "period": "WEEKLY"
    }
  }
}
```

`userId`는 Request Body로 전달받지 않는다.

현재 인증된 사용자의 정보를 서버에서 가져와 Strategy의 소유자로 설정한다.

---

## 18.2 전략 목록 조회

```http
GET /strategies
```

현재 로그인한 사용자가 소유한 Strategy 중 삭제되지 않은 Strategy를 조회한다.

조건:

```text
strategy.userId == authenticatedUser.id
AND
deletedAt == NULL
```

---

## 18.3 전략 상세 조회

```http
GET /strategies/{strategyId}
```

현재 로그인한 사용자가 소유한 Strategy를 조회한다.

다음 조건을 모두 만족해야 한다.

```text
strategyId 일치
AND
strategy.userId == authenticatedUser.id
AND
deletedAt == NULL
```

다른 사용자의 Strategy에는 접근할 수 없다.

---

## 18.4 전략 삭제

```http
DELETE /strategies/{strategyId}
```

Strategy Row를 실제로 삭제하지 않는다.

```text
deletedAt = 현재 시간
```

으로 변경한다.

삭제 이후 해당 Strategy는 일반적인 목록 및 상세 조회에서 제외한다.

---

## 18.5 전략 수정

전략 수정 API는 제공하지 않는다.

```http
PUT   /strategies/{strategyId}   X
PATCH /strategies/{strategyId}   X
```

사용자가 기존 전략을 수정하고 저장하는 경우 새로운 Strategy를 생성한다.

```text
기존 Strategy 조회
       ↓
사용자가 설정 변경
       ↓
POST /strategies
       ↓
새로운 Strategy 생성
```

---

# 19. 소유권 정책

Strategy는 생성한 사용자에게 귀속된다.

모든 조회 및 삭제 작업에서 다음 조건을 검증한다.

```text
strategy.userId == authenticatedUser.id
```

따라서:

```text
User A
 └── Strategy #1

User B
 └── Strategy #2
```

인 경우 User B는 Strategy #1을 조회하거나 삭제할 수 없다.

---

# 20. 도메인 책임 분리

Strategy 도메인은 **전략을 정의하고 저장하는 역할**을 담당한다.

실제 시장 데이터 계산 및 전략 실행은 Strategy 도메인의 책임이 아니다.

```text
Strategy
│
│ 전략 정의 / 저장
│
▼
Strategy DSL
│
├───────────────┐
▼               ▼
Backtest     Paper Trading
│               │
└───────┬───────┘
        ▼
 Strategy Engine
        │
        ├── Factor 계산
        ├── Filter 평가
        ├── Ranking
        ├── Top N Selection
        └── Target Portfolio 생성
```

따라서 Strategy Entity 내부에서 다음과 같은 로직을 직접 수행하지 않는다.

```text
RSI 계산
SMA 계산
Return 계산
Filter 실행
Ranking 실행
Top N 계산
주문 생성
백테스트 실행
```

Strategy는 **"어떤 전략을 실행할 것인가"를 표현하는 설정 객체**이다.

실행 Engine은 Strategy DSL을 읽어 실제 계산을 수행한다.

---

# 21. MVP 지원 범위

Strategy 도메인의 MVP 지원 범위는 다음과 같다.

### Strategy 관리

- Strategy 생성
- Strategy 목록 조회
- Strategy 상세 조회
- Strategy Soft Delete
- Immutable Strategy 정책

### Strategy DSL

- Timeframe
- Factor
- Value
- 비교 연산
- AND / OR
- 중첩 Filter Group
- Ranking
- Top N Selection
- Rebalance

### Factor

```text
RETURN
RSI
SMA_DISPARITY
VOLATILITY
VOLUME_RATIO
DRAWDOWN
```

### Timeframe

```text
1H
4H
1D
```

### Operator

```text
GT
GTE
LT
LTE
```

### Logic

```text
AND
OR
```

### Ranking Direction

```text
ASC
DESC
```

### Rebalance Period

```text
DAILY
WEEKLY
MONTHLY
```

---

# 22. Strategy 도메인 외 구현 범위

다음 기능은 Strategy 도메인 자체의 구현 범위에서 제외한다.

```text
Factor Engine
ta4j 연동
실제 기술적 지표 계산
Filter 실행 Engine
Ranking Engine
Top N 계산 Engine
Rebalancing Engine
Backtest
Paper Trading
실제 주문 체결
시장 데이터 수집
```

Strategy 도메인 구현 완료 후 별도의 실행 계층에서 위 기능을 구현한다.

---

# 23. 최종 구조

Strategy 도메인의 역할을 전체 시스템 기준으로 정리하면 다음과 같다.

```text
사용자
  │
  │ 전략 설정
  ▼
Strategy API
  │
  ▼
Strategy
  │
  └── Strategy DSL (JSONB)
          │
          │ 실행 시 참조
          ▼
    Strategy Engine
          │
          ├── Factor Engine
          ├── Filter
          ├── Ranking
          └── Selection
          │
          ▼
    Target Portfolio
       ┌──┴──┐
       ▼     ▼
  Backtest  Paper Trading
```

## 핵심 설계 원칙

1. **Strategy는 생성 이후 설정을 변경하지 않는 Immutable Snapshot이다.**
2. **전략 수정은 기존 Row UPDATE가 아니라 새로운 Strategy 생성으로 처리한다.**
3. **삭제는 Soft Delete로 처리한다.**
4. **전략 규칙은 PostgreSQL JSONB 기반 DSL로 저장한다.**
5. **Filter는 GROUP / CONDITION 재귀 구조를 사용하여 중첩 AND/OR를 지원한다.**
6. **Filter는 투자 가능 여부를 판단하고 Ranking은 통과 종목 간 우선순위를 결정한다.**
7. **Top N은 최대 보유 종목 수이며 N개를 강제로 채우지 않는다.**
8. **선택된 종목은 MVP에서 동일 비중으로 구성한다.**
9. **Strategy는 전략을 정의하며 실제 Factor 계산 및 실행은 별도의 Engine이 담당한다.**
10. **동일한 Strategy DSL을 Backtest와 Paper Trading에서 공통으로 사용한다.**