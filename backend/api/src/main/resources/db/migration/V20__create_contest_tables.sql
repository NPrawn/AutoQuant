CREATE SCHEMA IF NOT EXISTS contest;

CREATE TABLE contest.contest_round (
    round_id              UUID         NOT NULL,
    title                 VARCHAR(100) NOT NULL,
    submission_starts_at  TIMESTAMPTZ  NOT NULL,
    submission_ends_at    TIMESTAMPTZ  NOT NULL,
    phase                 VARCHAR(20)  NOT NULL,
    max_submissions       SMALLINT     NOT NULL,
    warmup_weeks          SMALLINT     NOT NULL,
    evaluation_weeks      SMALLINT     NOT NULL,
    scoring_started_at    TIMESTAMPTZ  NULL,
    finished_at           TIMESTAMPTZ  NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_contest_round PRIMARY KEY (round_id),
    CONSTRAINT ck_contest_round_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_contest_round_submission_period CHECK (submission_starts_at < submission_ends_at),
    CONSTRAINT ck_contest_round_phase CHECK (phase IN ('READY', 'SCORING', 'FINISHED')),
    CONSTRAINT ck_contest_round_max_submissions CHECK (max_submissions > 0),
    CONSTRAINT ck_contest_round_warmup_weeks CHECK (warmup_weeks > 0),
    CONSTRAINT ck_contest_round_evaluation_weeks CHECK (evaluation_weeks > 0),
    CONSTRAINT ck_contest_round_timeline CHECK (
        (scoring_started_at IS NULL OR scoring_started_at >= submission_ends_at)
        AND (finished_at IS NULL OR scoring_started_at IS NOT NULL)
        AND (finished_at IS NULL OR finished_at >= scoring_started_at)
    )
);

CREATE INDEX idx_contest_round_phase_submission_ends_at
    ON contest.contest_round (phase, submission_ends_at);

CREATE TABLE contest.market_dataset (
    dataset_id           UUID          NOT NULL,
    round_id             UUID          NOT NULL,
    kind                 VARCHAR(10)   NOT NULL,
    period_start         TIMESTAMPTZ   NOT NULL,
    period_end           TIMESTAMPTZ   NOT NULL,
    evaluation_return    NUMERIC(12,8) NOT NULL,
    source_window_start  TIMESTAMPTZ   NOT NULL,
    source_window_end    TIMESTAMPTZ   NOT NULL,
    seed                 BIGINT        NOT NULL,
    source_weeks         TEXT          NOT NULL,
    generator_version    VARCHAR(50)   NOT NULL,
    filled_hours         INTEGER       NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_market_dataset PRIMARY KEY (dataset_id),
    CONSTRAINT fk_market_dataset_round FOREIGN KEY (round_id)
        REFERENCES contest.contest_round (round_id) ON DELETE RESTRICT,
    CONSTRAINT uq_market_dataset_round_kind UNIQUE (round_id, kind),
    CONSTRAINT ck_market_dataset_kind CHECK (kind IN ('GENERAL', 'BULL', 'BEAR', 'SIDEWAYS')),
    CONSTRAINT ck_market_dataset_period CHECK (period_start < period_end),
    CONSTRAINT ck_market_dataset_source_window CHECK (source_window_start < source_window_end),
    CONSTRAINT ck_market_dataset_source_weeks CHECK (BTRIM(source_weeks) <> ''),
    CONSTRAINT ck_market_dataset_generator_version CHECK (BTRIM(generator_version) <> ''),
    CONSTRAINT ck_market_dataset_filled_hours CHECK (filled_hours >= 0)
);

CREATE TABLE contest.market_dataset_asset (
    dataset_id  UUID NOT NULL,
    asset_id    UUID NOT NULL,

    CONSTRAINT pk_market_dataset_asset PRIMARY KEY (dataset_id, asset_id),
    CONSTRAINT fk_market_dataset_asset_dataset FOREIGN KEY (dataset_id)
        REFERENCES contest.market_dataset (dataset_id) ON DELETE CASCADE,
    CONSTRAINT fk_market_dataset_asset_asset FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id) ON DELETE RESTRICT
);

CREATE TABLE contest.market_candle (
    dataset_id        UUID           NOT NULL,
    asset_id          UUID           NOT NULL,
    interval_minutes  SMALLINT       NOT NULL,
    open_time         TIMESTAMPTZ    NOT NULL,
    open_price        NUMERIC(30,12) NOT NULL,
    high_price        NUMERIC(30,12) NOT NULL,
    low_price         NUMERIC(30,12) NOT NULL,
    close_price       NUMERIC(30,12) NOT NULL,
    trade_volume      NUMERIC(38,18) NOT NULL,
    trade_amount      NUMERIC(38,8)  NOT NULL,

    CONSTRAINT pk_market_candle PRIMARY KEY (dataset_id, asset_id, interval_minutes, open_time),
    CONSTRAINT fk_market_candle_dataset_asset FOREIGN KEY (dataset_id, asset_id)
        REFERENCES contest.market_dataset_asset (dataset_id, asset_id) ON DELETE CASCADE,
    CONSTRAINT ck_market_candle_interval_minutes CHECK (interval_minutes IN (60, 240, 1440)),
    CONSTRAINT ck_market_candle_prices CHECK (
        open_price > 0 AND high_price > 0 AND low_price > 0 AND close_price > 0
        AND low_price <= open_price AND low_price <= close_price
        AND high_price >= open_price AND high_price >= close_price
    ),
    CONSTRAINT ck_market_candle_trade_volume CHECK (trade_volume >= 0),
    CONSTRAINT ck_market_candle_trade_amount CHECK (trade_amount >= 0)
);

ALTER TABLE trading.backtest_run
    ADD COLUMN contest_dataset_id UUID NULL;

ALTER TABLE trading.backtest_run
    ADD CONSTRAINT fk_backtest_run_contest_dataset FOREIGN KEY (contest_dataset_id)
        REFERENCES contest.market_dataset (dataset_id) ON DELETE RESTRICT;

CREATE INDEX idx_backtest_run_contest_dataset_id
    ON trading.backtest_run (contest_dataset_id)
    WHERE contest_dataset_id IS NOT NULL;
