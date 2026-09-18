-- 점수를 백분위에서 절대 환산으로 바꾼다 (ADR-0002, ADR-0003 개정).
-- 국면 칸은 이제 낙폭 백분위가 아니라 낙폭을 실격선으로 잰 점수라서 이름을 바꾼다.
ALTER TABLE contest.contest_result RENAME COLUMN bull_mdd_percentile TO bull_regime_score;
ALTER TABLE contest.contest_result RENAME COLUMN bear_mdd_percentile TO bear_regime_score;
ALTER TABLE contest.contest_result RENAME COLUMN sideways_mdd_percentile TO sideways_regime_score;

-- 산식이 바뀌어 기존 점수는 비교할 수 없다. 남은 결과를 비우고 다시 채점하게 한다.
DELETE FROM contest.contest_result;

-- 1회차 마감을 이번 주 일요일로 옮긴다.
UPDATE contest.contest_round
SET submission_ends_at = '2026-09-20T15:00:00Z',
    phase = 'READY',
    scoring_started_at = NULL,
    finished_at = NULL
WHERE round_id = '0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10';
