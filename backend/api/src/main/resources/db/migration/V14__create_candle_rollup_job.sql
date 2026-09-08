-- Source writes and invalidation commit together, including REST corrections.
CREATE TABLE ops.candle_rollup_job (
    asset_id UUID NOT NULL REFERENCES reference.asset(asset_id) ON DELETE CASCADE,
    interval_minutes SMALLINT NOT NULL CHECK (interval_minutes IN (2, 5, 15, 60, 240, 1440)),
    open_time TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (asset_id, interval_minutes, open_time)
);

CREATE INDEX idx_candle_rollup_job_recent
    ON ops.candle_rollup_job(open_time DESC, asset_id, interval_minutes);

CREATE FUNCTION ops.enqueue_candle_rollup() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO ops.candle_rollup_job(asset_id, interval_minutes, open_time)
    SELECT NEW.asset_id, width,
           date_bin(make_interval(mins => width), NEW.open_time, TIMESTAMPTZ '1970-01-01 00:00:00+00')
      FROM unnest(ARRAY[2, 5, 15, 60, 240, 1440]::smallint[]) AS width
    ON CONFLICT (asset_id, interval_minutes, open_time)
    DO UPDATE SET revision = ops.candle_rollup_job.revision + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER enqueue_candle_rollup
AFTER INSERT OR UPDATE ON market_data.minute_candle
FOR EACH ROW WHEN (NEW.interval_minutes = 1)
EXECUTE FUNCTION ops.enqueue_candle_rollup();

-- Queue existing history once; aggregation itself runs outside the migration.
INSERT INTO ops.candle_rollup_job(asset_id, interval_minutes, open_time)
SELECT DISTINCT asset_id, width,
       date_bin(make_interval(mins => width), open_time, TIMESTAMPTZ '1970-01-01 00:00:00+00')
  FROM market_data.minute_candle
 CROSS JOIN unnest(ARRAY[2, 5, 15, 60, 240, 1440]::smallint[]) AS width
 WHERE interval_minutes = 1;
