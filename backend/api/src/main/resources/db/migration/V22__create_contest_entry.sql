CREATE TABLE contest.contest_entry (
    entry_id            UUID        NOT NULL,
    round_id            UUID        NOT NULL,
    member_id           UUID        NOT NULL,
    strategy_id         BIGINT      NOT NULL,
    submission_count    SMALLINT    NOT NULL,
    first_submitted_at  TIMESTAMPTZ NOT NULL,
    last_submitted_at   TIMESTAMPTZ NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_contest_entry PRIMARY KEY (entry_id),
    CONSTRAINT fk_contest_entry_round FOREIGN KEY (round_id)
        REFERENCES contest.contest_round (round_id) ON DELETE RESTRICT,
    CONSTRAINT fk_contest_entry_member FOREIGN KEY (member_id)
        REFERENCES account.member (member_id) ON DELETE RESTRICT,
    CONSTRAINT fk_contest_entry_strategy FOREIGN KEY (strategy_id)
        REFERENCES strategy.strategy (strategy_id) ON DELETE RESTRICT,
    CONSTRAINT uq_contest_entry_round_member UNIQUE (round_id, member_id),
    CONSTRAINT ck_contest_entry_submission_count CHECK (submission_count > 0),
    CONSTRAINT ck_contest_entry_submitted_at CHECK (first_submitted_at <= last_submitted_at),
    CONSTRAINT ck_contest_entry_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_contest_entry_round_id
    ON contest.contest_entry (round_id);

CREATE INDEX idx_contest_entry_member_id
    ON contest.contest_entry (member_id);
