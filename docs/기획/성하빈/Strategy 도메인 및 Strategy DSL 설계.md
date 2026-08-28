# Strategy 도메인 및 Strategy DSL 설계

## 1. 설계 방향

ERD는 시스템에서 무엇을 저장해야 하는지가 결정된 이후 설계한다.

따라서 다음 순서로 시스템 구조를 구체화한다.

1. 핵심 사용자 흐름 정의
2. 도메인 경계 정의
3. 각 도메인에서 저장할 데이터 결정
4. 서비스 간 데이터 흐름 설계
5. ERD 설계
6. API 설계
7. Strategy DSL 설계
8. Engine 인터페이스 설계

### 핵심 사용자 흐름

```text
회원가입 / 로그인
        ↓
전략 생성
        ↓
전략 저장
        ↓
백테스트 실행
        ↓
결과 조회
        ↓
모의투자 시작
        ↓
모의투자 현황 조회
```

### 주요 도메인

```text
com.ssafy.a107

├── auth
├── market
├── strategy
├── backtest
├── papertrading
└── ranking
```

각 도메인의 주요 책임은 다음과 같다.

| 도메인 | 주요 책임 |
| --- | --- |
| Auth | 사용자 인증 및 인가 |
| Market | 시장 데이터 관리 |
| Strategy | 전략 및 전략 버전 관리 |
| Backtest | 전략 백테스트 실행 및 결과 관리 |
| Paper Trading | 모의투자 실행 및 계좌 관리 |
| Ranking | 전략 평가 및 랭킹 관리 |

### 주요 데이터 흐름

```text
Strategy
   │
   ├──────────────→ Backtest
   │
   └──────────────→ Paper Trading

Market Data
   ↓
Factor Engine
   ↓
Strategy Engine
   ↓
┌───────────────┐
│               │
Backtest    Paper Trading
```

ERD 설계 전 다음 사항을 우선 결정한다.

- **Strategy 저장 방식**
  - Strategy DSL을 JSON으로 저장
- **OHLCV 저장 방식**
  - 1분봉만 저장하고 상위 Timeframe을 생성할지
  - 1h / 4h / 1d 데이터를 별도로 저장할지
- **Backtest 실행 방식**
  - 동기 / 비동기 실행
  - Job 상태 관리
- **Paper Trading 구조**
  - 가상 계좌의 소유 단위
  - 사용자별 계좌 또는 전략별 계좌

---

# 2. Strategy 도메인

## 2.1 역할

Strategy 도메인은 **사용자가 만든 투자 전략 자체와 전략의 변경 이력을 관리**한다.

주요 책임은 다음과 같다.

- 전략 생성
- 전략 조회
- 전략 수정
- 전략 삭제
- 전략 버전 관리
- 공개 전략 복제
- 실행 가능한 Strategy DSL 저장

백테스트, 모의투자, 랭킹 결과는 Strategy 도메인에 직접 저장하지 않고 각각 별도 도메인에서 `StrategyVersion`을 참조한다.

```text
Strategy
    │
    └── StrategyVersion
              │
              ├── Backtest
              ├── PaperTrading
              └── Ranking
```

---

## 2.2 Strategy ERD

### 전체 관계

```text
USER
 │
 │ 1:N
 ▼
STRATEGY
 │
 │ 1:N
 ▼
STRATEGY_VERSION
 │
 ├─────────────┬──────────────┐
 ▼             ▼              ▼
BACKTEST   PAPER_TRADING   RANKING
```

핵심 관계는 다음과 같다.

> **User 1 : N Strategy 1 : N StrategyVersion**

---

## 2.3 Strategy

`Strategy`는 **전략의 정체성 및 메타데이터**를 나타낸다.

| 컬럼 | 설명 |
| --- | --- |
| `strategy_id` | PK |
| `user_id` | 전략 소유 사용자 FK |
| `name` | 전략명 |
| `description` | 전략 설명 |
| `created_at` | 생성 시각 |
| `updated_at` | 수정 시각 |

예를 들어 사용자가 `거래량 기반 모멘텀 전략`이라는 전략을 생성하면 하나의 Strategy가 생성된다.

```text
Strategy #17

name = 거래량 기반 모멘텀 전략
user_id = 3
```

전략의 구체적인 실행 조건은 Strategy에 저장하지 않는다.

---

# 3. StrategyVersion

## 3.1 역할

`StrategyVersion`은 **특정 시점의 실제 실행 가능한 전략 전체를 보관하는 불변 스냅샷**이다.

| 컬럼 | 설명 |
| --- | --- |
| `strategy_version_id` | PK |
| `strategy_id` | Strategy FK |
| `version` | 버전 번호 |
| `strategy_dsl` | 전략 정의 JSONB |
| `created_at` | 버전 생성 시각 |

다음 제약조건을 적용한다.

```sql
UNIQUE(strategy_id, version)
```

동일한 Strategy 내에서 같은 버전 번호가 중복되지 않도록 한다.

---

## 3.2 전략 수정과 버전 관리

전략을 수정할 때 기존 `StrategyVersion`을 UPDATE하지 않는다.

예:

```text
Strategy #17

├── v1
│   ├── RSI(14) < 70
│   └── RETURN(20) DESC
│
├── v2
│   ├── RSI(14) < 70
│   ├── VOLUME_RATIO(20) > 1.2
│   └── RETURN(20) DESC
│
└── v3
    └── ...
```

전략이 변경될 때마다 **새로운 StrategyVersion을 INSERT**한다.

이를 통해 과거 `v1`으로 수행했던 백테스트나 모의투자는 전략을 수정한 이후에도 당시 조건을 그대로 재현할 수 있다.

---

## 3.3 Strategy와 StrategyVersion을 분리하는 이유

Strategy 하나에 실행 조건을 저장하고 수정 시 UPDATE하는 경우:

```text
Strategy

RSI < 70
    ↓ 수정
RSI < 60
```

과거 백테스트가 어떤 조건으로 실행됐는지 재현하기 어려워진다.

따라서 두 개념을 분리한다.

```text
Strategy
= 전략 자체의 정체성

StrategyVersion
= 특정 시점의 실행 가능한 전략
```

실행 관련 도메인은 특정 `StrategyVersion`을 참조한다.

```text
Backtest
strategy_version_id = 15

PaperTrading
strategy_version_id = 18

Ranking
strategy_version_id = 21
```

이를 통해 **전략 수정이 이미 실행된 백테스트 및 모의투자 결과에 영향을 주지 않도록 한다.**

---

# 4. Strategy DSL

## 4.1 DSL의 역할

DSL은 `Domain Specific Language`의 약자로, **특정 도메인의 문제를 표현하기 위해 정의한 언어**다.

이 서비스에서는 사용자가 만든 투자 전략을 시스템이 저장·검증·실행할 수 있도록 표현하는 규칙으로 사용한다.

사용자가 UI에서 다음 전략을 생성했다고 가정한다.

```text
Filter

RSI(14) < 70
AND
VOLUME_RATIO(20) > 1.2

Ranking

RETURN(20) 높은 순

Selection

TOP 5

Rebalancing

주간
```

이를 시스템 내부의 Strategy DSL로 변환한다.

---

## 4.2 DSL 기본 구조

```json
{
  "timeframe": "4H",
  "filter": {
    "logic": "AND",
    "conditions": [
      {
        "factor": "RSI",
        "period": 14,
        "operator": "LT",
        "value": 70
      },
      {
        "factor": "VOLUME_RATIO",
        "period": 20,
        "operator": "GT",
        "value": 1.2
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

이 JSON 구조는 확정 스키마가 아니라 **설계 예시**다.

실제 Engine 설계 단계에서 다음 사항을 확정한다.

- 필드명
- 중첩 구조
- 지원 Factor
- 지원 Operator
- 값의 허용 범위
- Strategy DSL 버전 관리

---

# 5. UI → DSL → Engine

DSL은 사용자가 직접 작성하는 JSON이 아니다.

사용자는 노코드 UI에서 투자 전략을 구성한다.

```text
RSI
[14]

[<]

[70]

AND

Volume Ratio
[20]

[>]

[1.2]
```

사용자가 구성한 내용을 정해진 DSL 구조로 변환한다.

전체 흐름은 다음과 같다.

```text
No-Code UI
     ↓
Strategy DSL
     ↓
Strategy Validator
     ↓
StrategyVersion 저장
     ↓
Strategy Engine
     ↓
Filter → Ranking → Top N
     ↓
Target Portfolio
     ↓
┌──────────────┬──────────────┐
↓              ↓
Backtest    Paper Trading
```

즉 **Strategy DSL은 UI와 실행 엔진 사이의 Contract 역할**을 한다.

---

# 6. JSON DSL 저장 방식

## 6.1 전략을 관계형 테이블로 완전히 정규화하지 않는 이유

전략을 관계형 데이터로 표현하는 것도 가능하다.

예:

```text
STRATEGY_VERSION

FILTER_GROUP

FILTER_CONDITION

RANKING

SELECTION

REBALANCING
```

하지만 현재 전략 데이터의 특성에서는 **JSON DSL 방식이 더 적합하다.**

---

## 6.2 이유 1 — 전략은 조회 데이터보다 실행 규칙에 가깝다

일반적인 관계형 데이터는 개별 속성을 기준으로 검색하거나 관계를 조회하는 경우가 많다.

반면 Strategy DSL의 주요 사용 흐름은 다음과 같다.

```text
StrategyVersion 조회
        ↓
전략 정의 전체 로드
        ↓
Validator
        ↓
Strategy Engine 실행
```

조건 하나하나를 DB에서 독립적으로 조회하기보다는 **전략 전체를 하나의 실행 단위로 사용한다.**

---

## 6.3 이유 2 — 전략 조건은 계층적 구조를 가진다

AND / OR 조건이 확장되면 전략은 Tree 구조를 가질 수 있다.

예:

```text
(RSI < 70 AND VOLATILITY < 8)
OR
(VOLUME_RATIO > 1.5 AND RETURN > 0)
```

구조적으로 표현하면:

```text
              OR
            /    \
          AND    AND
         /  \    /  \
       RSI  VOL VR  RETURN
```

관계형 DB에서 이러한 재귀적 구조를 표현하려면 `ConditionGroup` 자기참조 등의 구조가 필요하다.

JSON에서는 다음과 같이 계층 구조를 그대로 표현할 수 있다.

```json
{
  "operator": "OR",
  "children": [
    {
      "operator": "AND",
      "children": []
    },
    {
      "operator": "AND",
      "children": []
    }
  ]
}
```

---

## 6.4 이유 3 — StrategyVersion 스냅샷 관리

전략 버전은 불변 데이터로 관리한다.

```text
Strategy

├── v1 → DSL 전체
├── v2 → DSL 전체
└── v3 → DSL 전체
```

따라서 `v2`를 사용하는 백테스트는 언제든 다음 구조로 당시 전략을 재현할 수 있다.

```text
Backtest
   ↓
StrategyVersion v2
   ↓
v2의 DSL 전체
```

관계형 구조로 전략을 여러 테이블에 분산하면 새로운 버전을 만들 때 해당 버전의 Filter, Ranking, Selection 등의 관계 데이터도 함께 관리해야 한다.

JSON DSL에서는 **전략 전체가 하나의 스냅샷**이 된다.

---

## 6.5 이유 4 — 전략 구조 확장

현재 전략은 다음 구조를 가진다.

```text
Filter
Ranking
Top N
Rebalancing
```

향후 다음과 같은 개념이 추가될 수 있다.

```text
Filter
Ranking
Top N
Rebalancing
Risk Rule
Market Condition
...
```

관계형 구조에서는 새로운 전략 개념이 추가될 때 테이블 또는 컬럼 구조 변경이 필요할 수 있다.

JSON DSL은 DSL 스키마를 버전 관리하면서 상대적으로 유연하게 확장할 수 있다.

---

# 7. JSON DSL의 단점과 Validator

JSONB라고 해서 항상 장점만 존재하는 것은 아니다.

DB 차원에서는 잘못된 DSL이 저장되는 것을 관계형 컬럼만큼 강하게 통제하기 어렵다.

예:

```json
{
  "factor": "ABC",
  "period": -100,
  "topN": 99999
}
```

따라서 JSON DSL 구조에서는 **Strategy Validator가 중요한 역할**을 한다.

```text
Strategy DSL
      ↓
Strategy Validator
      │
      ├─ 지원하는 Factor인가?
      ├─ 지원하는 Operator인가?
      ├─ period 범위가 유효한가?
      ├─ Top N이 유효한가?
      ├─ 논리 구조가 올바른가?
      └─ 미래 데이터 참조가 존재하는가?
      ↓
Validation 성공
      ↓
StrategyVersion 저장
```

즉 DB 정규화를 줄이는 대신 **애플리케이션 계층에서 전략 스키마와 의미적 유효성을 검증**한다.

---

# 8. 최종 저장 구조

Strategy 도메인은 **RDB + JSON DSL의 Hybrid 구조**로 구성한다.

## 8.1 RDB

식별, 소유권, 관계, 검색에 필요한 정보를 관계형 데이터로 관리한다.

```text
STRATEGY
- strategy_id
- user_id
- name
- description
- created_at
- updated_at

STRATEGY_VERSION
- strategy_version_id
- strategy_id
- version
- created_at
```

## 8.2 JSONB

실제로 실행할 전략 규칙은 `StrategyVersion`에 JSONB로 저장한다.

```text
STRATEGY_VERSION.strategy_dsl
```

### 설계 원칙

> **전략의 메타데이터와 엔티티 관계는 관계형 모델로 관리하고, 계층적이며 변경 가능성이 높은 실행 규칙은 JSON DSL로 관리한다. StrategyVersion은 DSL 전체를 불변 스냅샷으로 보관하여 백테스트와 모의투자에서 동일한 전략을 재현할 수 있도록 한다.**

---

# 9. DB Schema

```sql
CREATE TABLE strategy (
    strategy_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_strategy_user_id
    ON strategy(user_id);

CREATE TABLE strategy_version (
    strategy_version_id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    strategy_dsl JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_strategy_version_strategy
        FOREIGN KEY (strategy_id)
        REFERENCES strategy(strategy_id)
        ON DELETE CASCADE,

    CONSTRAINT uk_strategy_version
        UNIQUE (strategy_id, version),

    CONSTRAINT ck_strategy_version_positive
        CHECK (version > 0)
);
```

---

# 10. 중첩 조건을 지원하는 Strategy DSL

단순한 `AND` 조건뿐 아니라 중첩된 `AND / OR` 조건을 지원하기 위해 Filter를 Tree 구조로 표현할 수 있다.

예를 들어 다음 조건:

```text
(RSI(20) < RSI(60) AND VOLUME_RATIO(20) < 1.2)
OR
(RETURN(20) > 5)
```

은 다음과 같이 표현할 수 있다.

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

---

# 11. DSL 저장 방식 비교

Strategy DSL 전체를 하나의 JSONB로 저장하는 방식과 일부 값을 컬럼으로 분리하는 방식을 비교한다.

| 비교 항목 | ① 통짜 DSL JSONB | ② 일부 컬럼 분리 + JSONB |
| --- | --- | --- |
| 예시 | `strategy_dsl JSONB` 하나 | `timeframe`, `top_n`, `filter_rule JSONB`, `ranking_rule JSONB` 등 |
| 기본 아이디어 | 실행에 필요한 전략 전체를 하나의 JSON 스냅샷으로 저장 | 단순한 값은 컬럼, 복잡한 조건식만 JSON으로 저장 |
| DB 구조 | **단순함** | 상대적으로 복잡함 |
| 전략 조회 | PK로 DSL 전체 조회하기 편함 | 여러 컬럼을 읽어서 전략 객체로 조립해야 함 |
| Engine 연동 | **DSL 전체를 바로 역직렬화해서 전달 가능** | 컬럼 + JSON을 조합해 실행 모델 생성 필요 |
| 버전 관리 | **매우 편함. JSON 전체가 하나의 불변 스냅샷** | 버전별 여러 컬럼 + JSON이 함께 하나의 스냅샷을 구성 |
| 전략 복제 | **JSON 전체 복사로 간단** | 여러 컬럼과 JSON을 모두 복사해야 함 |
| 중첩 AND/OR | **JSON Tree로 표현하기 적합** | `filter_rule JSONB` 내부에서는 동일하게 가능 |
| 복합 Ranking 확장 | **DSL 구조만 확장하면 됨** | `ranking_rule JSONB` 내부 확장 가능 |
| 새로운 전략 설정 추가 | **DSL 구조만 확장 가능** | 컬럼으로 관리한다면 DB Migration 필요 |
| DB에서 조건 검색 | 불편함. JSON 내부 조회 필요 | **편함** |
| DB Constraint | 상대적으로 약함 → Validator 중요 | **CHECK, 타입 등 DB 제약 활용 가능** |
| ERD 가시성 | 전략 내부 설정이 보이지 않음 | **어떤 설정이 있는지 파악하기 쉬움** |
| 데이터 정합성 | DSL 하나만 관리하므로 **단순** | 컬럼과 JSON의 책임 경계를 명확히 해야 함 |
| 확장성 | **높음** | 중간~높음 |
| 구현 복잡도 | **낮음** | 상대적으로 높음 |
| 적합한 경우 | 전략 전체를 읽어 Engine에서 실행하는 경우 | 전략 설정을 DB에서 자주 검색/집계하는 경우 |

---

# 12. 최종 설계 결정

현재 시스템에서는 **Strategy DSL 전체를 `strategy_dsl JSONB` 하나에 저장하는 방식을 기본 구조로 채택한다.**

주요 이유는 다음과 같다.

1. 전략 전체가 하나의 실행 단위다.
2. StrategyVersion을 불변 스냅샷으로 관리하기 쉽다.
3. 중첩 `AND / OR` 조건을 Tree 구조로 표현할 수 있다.
4. Strategy Engine으로 전달할 때 별도의 데이터 조립 과정이 적다.
5. 새로운 Factor 및 전략 옵션 추가 시 DB Schema 변경을 최소화할 수 있다.

대신 JSONB 내부의 데이터 정합성은 `Strategy Validator`에서 검증한다.

최종 구조는 다음과 같다.

```text
User
 ↓
Strategy
 ↓
StrategyVersion
 ├── metadata → RDB Column
 └── strategy_dsl → JSONB
                       ↓
                Strategy Validator
                       ↓
                 Strategy Engine
                  /          \
                 ↓            ↓
            Backtest    Paper Trading
```

이를 통해 **전략 정의 → 저장 → 검증 → 실행 → 백테스트/모의투자**에서 동일한 Strategy DSL을 일관되게 사용할 수 있도록 한다.