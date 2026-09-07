# 트러블슈팅

막혔다 푼 것을 쌓는다. **푼 직후에 적는다.** 시간이 지나면 오류 문구를 정확히 기억하지 못하고, 부정확한 증상은 검색되지 않아 기록의 값이 사라진다.

## 적는 형식

각 항목은 넷을 갖춘다.

- **증상** — 실제로 본 오류 문구나 동작. 요약하지 말고 그대로 붙인다
- **원인** — 왜 그랬는지. 모르면 "미상"이라고 적는다. 추측을 원인으로 적지 않는다
- **해결** — 무엇을 어떻게 바꿨는지. 명령이나 코드 조각으로
- **교훈** — 다음에 같은 부류를 만나면 어디부터 볼지. 없으면 생략

날짜와 관련 작업 ID를 제목에 단다.

## 미리 예상되는 것

아직 겪지 않았다. 겪으면 아래에 항목으로 옮긴다.

| 후보 | 어디서 |
|---|---|
| GMS 프록시가 `response_format`의 `json_schema`를 통과시키지 않음 | F-3 |
| GMS 응답의 오류 형태가 OpenAI 원본과 달라 `openai` SDK 예외 매핑이 어긋남 | F-2 |
| 필수 환경변수 누락 예외가 임포트가 아니라 첫 요청에서 터짐 | A-2 |
| `/health/ready`가 프롬프트 파일 부재로 503 — 정상 동작인데 장애로 오해 | A-3 |
| 린터가 `health.py`의 설정 검사용 미사용 임포트를 F401로 제거 | C-2 |
| Spring 타임아웃이 우리 최대 소요(30초 × 재시도)보다 짧아 정상 응답이 타임아웃으로 보임 | L-4 |

---

<!-- 항목은 최신순으로 위에 쌓는다 -->

## 2026-09-07 — 편집 요청이 사용자가 정해 둔 값을 기본값으로 되돌린다 (K-3, `-162`)

**증상** — 시작 전략이 `4H`·`RSI(21)`·`ranking RETURN 30`·`topN 7`·`MONTHLY`인 상태에서 "순위 기준을 변동성으로 바꿔줘"를 보내면 `ranking.factor`는 요청대로 바뀌지만 `ranking.period`가 30에서 20으로 함께 바뀐다. "순위 기간을 30일로 봐줘"에서는 `timeframe`이 `4H`에서 `1D`로 바뀌고 `assumptions`에 "기본 시간프레임은 1D로 적용했습니다"가 남는다. 검증은 통과하고 `changes`에도 요청한 필드만 적히므로 화면에서 눈치채기 어렵다.

**원인** — 시스템 프롬프트의 기본값 규칙(`RETURN 20`, `1D`)에 적용 범위가 없었다. 신규 생성과 편집이 같은 규칙을 보고, 편집에서도 "값이 비어 있지 않은데 기본값을 쓴다"가 규칙 위반이 아니었다. `edit.txt`의 "Preserve information that the request does not change"는 무엇을 보존하라는 말인지가 모호해 기본값 규칙을 이기지 못한다.

**해결** — `edit.txt`에서 편집의 기본값 적용 범위를 못 박았다.

```
Change only the fields the request asks about; copy every other field from the
current strategy unchanged, including periods, timeframe, topN and rebalance.
The default values in the domain rules apply only to fields the current
strategy does not already have: never replace a value the user already set
with a default.
```

편집 골든 12건 측정에서 보존 실패 2건(`E-05`·`E-11`)이 0건이 됐다.

**교훈** — 편집 경로의 실패는 "안 바뀌어야 할 것이 바뀐 것"이라 통과/실패 한 칸으로는 안 보인다. `tests/edit_runner.py`가 `changedPaths` 밖의 모든 경로를 시작 상태와 자동 비교하는 이유가 이것이다. 프롬프트 규칙을 새로 쓸 때는 그 규칙이 신규 생성용인지 편집에도 적용되는지를 문장에 적는다.

## 2026-09-07 — 조건을 하나 지우면 필터 루트가 `GROUP`에서 `CONDITION`으로 벗겨진다 (K-3, `-162`)

**증상** — 조건 둘짜리 `GROUP`에서 "거래량 조건은 빼줘"를 보내면 남은 조건이 `GROUP`에 담기지 않고 루트가 그대로 `CONDITION`이 된다.

```json
{"type": "CONDITION", "left": {"type": "FACTOR", "factor": "RSI", "period": 21},
 "operator": "LT", "right": {"type": "VALUE", "value": 35.0}}
```

**원인** — 스키마상 `FilterNode`는 `GROUP`과 `CONDITION`을 모두 허용하므로 검증에 걸리지 않는다. 프롬프트에 루트 형태에 대한 규칙이 없어 모델이 조건 하나에 `GROUP` 껍데기를 남길 이유가 없었다.

**해결** — 시스템 프롬프트에 한 줄을 넣었다.

```
The root of filters is always a GROUP node, even when only one condition
remains after an edit.
```

**교훈** — 스키마가 허용하는 형태가 둘 이상이면 어느 쪽을 쓸지는 프롬프트가 정해야 한다. 검증기는 둘 다 통과시키므로 형태가 회차마다 흔들려도 아무 신호가 안 뜬다.

## 2026-09-07 — 관용구 매핑 한 줄이 관용구가 아닌 입력까지 끌어간다 (K-3, `-132` 항목 2)

**증상** — 프롬프트에 관용구 표를 넣은 직후 `U-01`("상승 추세가 강한 코인을 사고 싶어")과 `U-09`("비트코인이 상승 추세일 때만 알트코인을 사고 싶어")가 `RETURN 20`에서 `RETURN 60`으로 바뀌어 실패했다. 표를 넣기 전에는 둘 다 통과하던 건이다.

**원인** — 표에 넣은 줄이 `상승 추세를 오래 가져간다, 손실은 짧게 끊는다: RETURN 60`이었다. 모델이 이 줄을 "상승 추세"라는 짧은 표현에도 적용했다. 관용구 항목의 핵심어가 일반 표현과 겹치면 표가 그 일반 표현까지 덮는다.

**해결** — 겹치는 짧은 표현에 줄을 따로 주고, 긴 관용구는 원문에 가깝게 적었다.

```
- 상승 추세, 상승 추세가 강하다, with no period in the request: RETURN 20 GT 0.
  Use the default period here; only a phrase that itself points at a longer
  horizon moves it.
- 손실은 짧게 끊고 상승 추세는 오래 가져간다: RETURN 60 GT 0, ranked by RETURN 60 DESC.
```

**교훈** — 관용구 표를 늘릴 때는 새 줄이 기존에 통과하던 짧은 입력을 삼키지 않는지 본다. 관용구 하나를 고치고 회차를 돌리면 어느 줄이 범인인지 바로 나온다. 표가 커질수록 이 간섭이 늘어난다.
