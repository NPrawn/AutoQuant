CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS reference;

CREATE TABLE account.member (
    member_id                 UUID         NOT NULL,
    login_provider            VARCHAR(30)  NOT NULL,
    login_provider_member_id  VARCHAR(255) NOT NULL,
    email_address             VARCHAR(320) NOT NULL,
    display_name              VARCHAR(50)  NOT NULL,
    member_status             VARCHAR(20)  NOT NULL,
    last_login_at             TIMESTAMPTZ  NULL,
    withdrawn_at              TIMESTAMPTZ  NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_member
        PRIMARY KEY (member_id),
    CONSTRAINT uq_member_login_provider_identity
        UNIQUE (login_provider, login_provider_member_id),
    CONSTRAINT uq_member_email_address
        UNIQUE (email_address),
    CONSTRAINT ck_member_login_provider
        CHECK (login_provider IN ('SSAFY')),
    CONSTRAINT ck_member_login_provider_member_id_not_blank
        CHECK (BTRIM(login_provider_member_id) <> ''),
    CONSTRAINT ck_member_email_address_normalized
        CHECK (BTRIM(email_address) <> '' AND email_address = LOWER(email_address)),
    CONSTRAINT ck_member_display_name_not_blank
        CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT ck_member_status
        CHECK (member_status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_member_withdrawal_state
        CHECK (
            (member_status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
            OR (member_status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
        )
);

CREATE TABLE reference.asset (
    asset_id                     UUID         NOT NULL,
    exchange                     VARCHAR(20)  NOT NULL,
    exchange_market_code         VARCHAR(30)  NOT NULL,
    base_currency_code           VARCHAR(10)  NOT NULL,
    quote_currency_code          VARCHAR(10)  NOT NULL,
    korean_name                  VARCHAR(100) NOT NULL,
    english_name                 VARCHAR(100) NOT NULL,
    trading_status               VARCHAR(20)  NOT NULL,
    trading_support_started_at   TIMESTAMPTZ  NULL,
    trading_support_ended_at     TIMESTAMPTZ  NULL,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_asset
        PRIMARY KEY (asset_id),
    CONSTRAINT uq_asset_exchange_market_code
        UNIQUE (exchange, exchange_market_code),
    CONSTRAINT ck_asset_exchange
        CHECK (exchange IN ('UPBIT')),
    CONSTRAINT ck_asset_exchange_market_code_normalized
        CHECK (BTRIM(exchange_market_code) <> '' AND exchange_market_code = UPPER(exchange_market_code)),
    CONSTRAINT ck_asset_currency_codes
        CHECK (
            BTRIM(base_currency_code) <> ''
            AND BTRIM(quote_currency_code) <> ''
            AND base_currency_code = UPPER(base_currency_code)
            AND quote_currency_code = UPPER(quote_currency_code)
            AND base_currency_code <> quote_currency_code
        ),
    CONSTRAINT ck_asset_names_not_blank
        CHECK (BTRIM(korean_name) <> '' AND BTRIM(english_name) <> ''),
    CONSTRAINT ck_asset_trading_status
        CHECK (trading_status IN ('TRADING', 'SUSPENDED', 'DELISTED')),
    CONSTRAINT ck_asset_trading_period
        CHECK (
            trading_support_started_at IS NULL
            OR trading_support_ended_at IS NULL
            OR trading_support_started_at <= trading_support_ended_at
        ),
    CONSTRAINT ck_asset_trading_end_state
        CHECK (
            (trading_status = 'DELISTED' AND trading_support_ended_at IS NOT NULL)
            OR (trading_status <> 'DELISTED' AND trading_support_ended_at IS NULL)
        )
);

CREATE INDEX idx_asset_exchange_quote_trading_status
    ON reference.asset (exchange, quote_currency_code, trading_status);

LOCK TABLE market_data.minute_candle IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM market_data.minute_candle) THEN
        RAISE EXCEPTION
            'minute_candle contains rows; asset UUID mapping must be backfilled before V3 can be applied';
    END IF;
END
$$;

ALTER TABLE market_data.minute_candle
    DROP CONSTRAINT pk_minute_candle;

ALTER TABLE market_data.minute_candle
    RENAME COLUMN market_id TO asset_id;

ALTER TABLE market_data.minute_candle
    ALTER COLUMN asset_id TYPE UUID USING NULL::UUID;

ALTER TABLE market_data.minute_candle
    ADD CONSTRAINT pk_minute_candle
        PRIMARY KEY (asset_id, interval_minutes, open_time),
    ADD CONSTRAINT fk_minute_candle_asset
        FOREIGN KEY (asset_id)
        REFERENCES reference.asset (asset_id)
        ON DELETE RESTRICT;
