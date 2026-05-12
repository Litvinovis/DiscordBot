CREATE TABLE IF NOT EXISTS sandbox_users (
    user_id                TEXT PRIMARY KEY,
    user_name              TEXT,
    cash                   DOUBLE PRECISION,
    borrowed               DOUBLE PRECISION,
    total_fees             DOUBLE PRECISION,
    daily_baseline_date    TEXT,
    daily_baseline_equity  DOUBLE PRECISION,
    weekly_baseline_date   TEXT,
    weekly_baseline_equity DOUBLE PRECISION,
    monthly_baseline_date  TEXT,
    monthly_baseline_equity DOUBLE PRECISION,
    currency_holdings      TEXT,
    schema_version         INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_positions (
    position_key   TEXT PRIMARY KEY,
    user_id        TEXT,
    ticker         TEXT,
    instrument_id  TEXT,
    quantity       INTEGER,
    avg_price      DOUBLE PRECISION,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_trades (
    id              TEXT PRIMARY KEY,
    user_id         TEXT,
    ticker          TEXT,
    trade_side      TEXT,
    qty             INTEGER,
    price           DOUBLE PRECISION,
    fee             DOUBLE PRECISION,
    trade_timestamp BIGINT,
    schema_version  INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_limit_orders (
    id             TEXT PRIMARY KEY,
    user_id        TEXT,
    user_name      TEXT,
    ticker         TEXT,
    trade_side     TEXT,
    qty            INTEGER,
    limit_price    DOUBLE PRECISION,
    created_at     BIGINT,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_stop_orders (
    id             TEXT PRIMARY KEY,
    order_type     TEXT,
    user_id        TEXT,
    ticker         TEXT,
    trigger_price  DOUBLE PRECISION,
    created_at     BIGINT,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_price_alerts (
    id             TEXT PRIMARY KEY,
    user_id        TEXT,
    ticker         TEXT,
    target_price   DOUBLE PRECISION,
    above          BOOLEAN,
    created_at     BIGINT,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS dca_orders (
    id             BIGSERIAL PRIMARY KEY,
    user_id        TEXT NOT NULL,
    ticker         TEXT NOT NULL,
    amount_rub     DOUBLE PRECISION NOT NULL,
    frequency      TEXT NOT NULL,
    next_execution BIGINT NOT NULL,
    created_at     BIGINT NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (user_id, ticker)
);
