-- 수익률 −30% 미만·최대 낙폭 50% 초과 실격을 없앤다 (ADR-0003 개정, S15P21A107-249).
-- 크게 잃거나 깊이 빠진 전략은 실격 대신 시장 대비 점수가 0점으로 내려간다.
-- 두 사유로 저장된 결과가 새 제약에 걸리므로 결과를 모두 지우고 다시 채점한다.

DELETE FROM contest.contest_result;

ALTER TABLE contest.contest_result
    DROP CONSTRAINT ck_contest_result_disqualification_reason,
    ADD CONSTRAINT ck_contest_result_disqualification_reason CHECK (
        disqualification_reason IS NULL
        OR disqualification_reason IN ('BACKTEST_FAILED', 'LOW_EXPOSURE')
    );

-- 채점 스케줄러는 제출 중·채점 중 회차만 점수를 쓴다. 이미 끝난 회차는 채점 중으로 되돌려 새 규칙으로 다시 끝낸다.
UPDATE contest.contest_round
SET phase = 'SCORING',
    finished_at = NULL
WHERE phase = 'FINISHED';
