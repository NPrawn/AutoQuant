-- 순위표가 읽히게 일반장 수익률과 최대 낙폭을 채점 결과에 같이 둔다.
--
-- 새 척도에서 수익률 점수가 -101 같은 값이라 화면에서 퍼센트로 오해된다. 실제 수익률 %와 낙폭을
-- 같이 보여 주려는데 그 값은 backtest_result 에 있어 순위 질의가 조인을 세 번 더 타야 한다.
-- 채점할 때 같이 적어 두면 조회가 그대로고 숫자가 점수와 같은 시점에 고정된다.
--
-- 타입은 backtest_result 와 맞춘다. ck_contest_result_scores 에는 넣지 않는다. 아래 백필이 닿지
-- 않는 행(백테스트 결과가 지워진 옛 제출)이 있어도 마이그레이션이 실패하면 안 된다.

ALTER TABLE contest.contest_result
    ADD COLUMN general_return NUMERIC(14,8) NULL;

ALTER TABLE contest.contest_result
    ADD COLUMN general_mdd NUMERIC(14,8) NULL;

COMMENT ON COLUMN contest.contest_result.general_return IS '일반장 누적 수익률. 수익률 점수의 원본';
COMMENT ON COLUMN contest.contest_result.general_mdd IS '일반장 최대 낙폭';

-- 이미 채점된 회차를 채운다. 안 채우면 1회차 순위표의 두 열이 빈칸이 된다.
-- 회차가 FINISHED 라 채점 스케줄러가 다시 쓰지 않기 때문이다.
UPDATE contest.contest_result AS result
   SET general_return = backtest.cumulative_return,
       general_mdd    = backtest.mdd,
       updated_at     = CURRENT_TIMESTAMP
  FROM contest.contest_evaluation AS evaluation
  JOIN contest.market_dataset AS dataset
    ON dataset.dataset_id = evaluation.dataset_id
   AND dataset.kind = 'GENERAL'
  JOIN trading.backtest_result AS backtest
    ON backtest.backtest_id = evaluation.backtest_id
 WHERE evaluation.entry_id = result.entry_id
   AND result.disqualification_reason IS NULL;
