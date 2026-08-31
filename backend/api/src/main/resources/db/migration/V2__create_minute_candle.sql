CREATE SCHEMA IF NOT EXISTS market_data;

CREATE TABLE market_data.minute_candle (
    market_id          BIGINT          NOT NULL,
    interval_minutes   SMALLINT        NOT NULL,
    open_time          TIMESTAMPTZ     NOT NULL,
    open_price         NUMERIC(30, 12) NOT NULL,
    high_price         NUMERIC(30, 12) NOT NULL,
    low_price          NUMERIC(30, 12) NOT NULL,
    close_price        NUMERIC(30, 12) NOT NULL,
    trade_volume       NUMERIC(38, 18) NOT NULL,
    trade_amount       NUMERIC(38, 8)  NOT NULL,
    last_event_time    TIMESTAMPTZ     NOT NULL,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_minute_candle
        PRIMARY KEY (market_id, interval_minutes, open_time),
    CONSTRAINT ck_minute_candle_interval_positive
        CHECK (interval_minutes > 0),
    CONSTRAINT ck_minute_candle_price_non_negative
        CHECK (
            open_price >= 0
            AND high_price >= 0
            AND low_price >= 0
            AND close_price >= 0
        ),
    CONSTRAINT ck_minute_candle_price_range
        CHECK (
            high_price >= low_price
            AND open_price BETWEEN low_price AND high_price
            AND close_price BETWEEN low_price AND high_price
        ),
    CONSTRAINT ck_minute_candle_trade_non_negative
        CHECK (trade_volume >= 0 AND trade_amount >= 0)
);
