CREATE TABLE contest.contest_evaluation (
    entry_id          UUID          NOT NULL,
    dataset_id        UUID          NOT NULL,
    backtest_id       UUID          NOT NULL,
    average_exposure  NUMERIC(9,8)  NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_contest_evaluation PRIMARY KEY (entry_id, dataset_id),
    CONSTRAINT uq_contest_evaluation_backtest UNIQUE (backtest_id),
    CONSTRAINT fk_contest_evaluation_entry FOREIGN KEY (entry_id)
        REFERENCES contest.contest_entry (entry_id) ON DELETE CASCADE,
    CONSTRAINT fk_contest_evaluation_dataset FOREIGN KEY (dataset_id)
        REFERENCES contest.market_dataset (dataset_id) ON DELETE RESTRICT,
    CONSTRAINT fk_contest_evaluation_backtest FOREIGN KEY (backtest_id)
        REFERENCES trading.backtest_run (backtest_id) ON DELETE RESTRICT,
    CONSTRAINT ck_contest_evaluation_average_exposure CHECK (
        average_exposure IS NULL OR (average_exposure >= 0 AND average_exposure <= 1)
    )
);

CREATE INDEX idx_contest_evaluation_dataset_id
    ON contest.contest_evaluation (dataset_id);

CREATE TABLE contest.contest_result (
    entry_id                  UUID         NOT NULL,
    return_score              NUMERIC(6,3) NULL,
    bull_mdd_percentile       NUMERIC(6,3) NULL,
    bear_mdd_percentile       NUMERIC(6,3) NULL,
    sideways_mdd_percentile   NUMERIC(6,3) NULL,
    stability_score           NUMERIC(6,3) NULL,
    total_score               NUMERIC(6,3) NULL,
    ranking                   INTEGER      NULL,
    disqualification_reason   VARCHAR(30)  NULL,
    disqualified_kind         VARCHAR(10)  NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_contest_result PRIMARY KEY (entry_id),
    CONSTRAINT fk_contest_result_entry FOREIGN KEY (entry_id)
        REFERENCES contest.contest_entry (entry_id) ON DELETE CASCADE,
    CONSTRAINT ck_contest_result_disqualification_reason CHECK (
        disqualification_reason IS NULL
        OR disqualification_reason IN ('BACKTEST_FAILED', 'RETURN_BELOW_LIMIT', 'MDD_ABOVE_LIMIT', 'LOW_EXPOSURE')
    ),
    CONSTRAINT ck_contest_result_disqualified_kind CHECK (
        disqualified_kind IS NULL
        OR disqualified_kind IN ('GENERAL', 'BULL', 'BEAR', 'SIDEWAYS')
    ),
    CONSTRAINT ck_contest_result_scores CHECK (
        (disqualification_reason IS NULL
            AND return_score IS NOT NULL AND stability_score IS NOT NULL
            AND total_score IS NOT NULL AND ranking IS NOT NULL
            AND bull_mdd_percentile IS NOT NULL AND bear_mdd_percentile IS NOT NULL
            AND sideways_mdd_percentile IS NOT NULL)
        OR (disqualification_reason IS NOT NULL
            AND return_score IS NULL AND stability_score IS NULL
            AND total_score IS NULL AND ranking IS NULL
            AND bull_mdd_percentile IS NULL AND bear_mdd_percentile IS NULL
            AND sideways_mdd_percentile IS NULL)
    ),
    CONSTRAINT ck_contest_result_ranking CHECK (ranking IS NULL OR ranking > 0)
);

CREATE INDEX idx_contest_result_ranking
    ON contest.contest_result (ranking)
    WHERE ranking IS NOT NULL;
