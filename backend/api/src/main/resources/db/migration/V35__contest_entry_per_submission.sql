-- 대회 참가를 회원당 한 행에서 제출당 한 행으로 바꾼다.
--
-- 그동안은 회원이 다시 제출하면 같은 행을 덮어쓰고 submission_count 만 올렸다. 순위에는 마지막 제출
-- 하나만 올라갔고, 제출 횟수는 max_submissions 로 막혀 있었다. 이제 제출마다 행이 쌓이고 순위도
-- 제출 단위로 매긴다. 횟수 제한은 없앤다.
--
-- 기존 행은 그대로 둔다. 회원당 한 행이던 것이 "그 회원의 마지막 제출 한 건"이 되어 뜻이 이어진다.

ALTER TABLE contest.contest_entry
    DROP CONSTRAINT uq_contest_entry_round_member;

ALTER TABLE contest.contest_entry
    DROP CONSTRAINT ck_contest_entry_submission_count;

ALTER TABLE contest.contest_entry
    DROP CONSTRAINT ck_contest_entry_submitted_at;

ALTER TABLE contest.contest_entry
    DROP COLUMN submission_count;

ALTER TABLE contest.contest_entry
    DROP COLUMN first_submitted_at;

ALTER TABLE contest.contest_entry
    RENAME COLUMN last_submitted_at TO submitted_at;

-- 순위 화면이 회차의 제출을 제출 시각 순으로 읽는다.
CREATE INDEX idx_contest_entry_round_submitted_at
    ON contest.contest_entry (round_id, submitted_at DESC);

ALTER TABLE contest.contest_round
    DROP CONSTRAINT ck_contest_round_max_submissions;

ALTER TABLE contest.contest_round
    DROP COLUMN max_submissions;
