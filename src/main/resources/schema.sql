CREATE TABLE IF NOT EXISTS sandbox_users (
    user_id                TEXT PRIMARY KEY,
    user_name              TEXT,
    cash                   NUMERIC(19,8),
    borrowed               NUMERIC(19,8),
    total_fees             NUMERIC(19,8),
    daily_baseline_date    DATE,
    daily_baseline_equity  NUMERIC(19,8),
    weekly_baseline_date   DATE,
    weekly_baseline_equity NUMERIC(19,8),
    monthly_baseline_date  DATE,
    monthly_baseline_equity NUMERIC(19,8),
    currency_holdings      TEXT,
    schema_version         INTEGER,
    last_replenish_date    DATE,
    morning_digest_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS sandbox_positions (
    position_key   TEXT PRIMARY KEY,
    user_id        TEXT,
    ticker         TEXT,
    instrument_id  TEXT,
    quantity       INTEGER,
    avg_price      NUMERIC(19,8),
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_trades (
    id              TEXT PRIMARY KEY,
    user_id         TEXT,
    ticker          TEXT,
    trade_side      TEXT,
    qty             INTEGER,
    price           NUMERIC(19,8),
    fee             NUMERIC(19,8),
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
    limit_price    NUMERIC(19,8),
    created_at     BIGINT,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_stop_orders (
    id             TEXT PRIMARY KEY,
    order_type     TEXT,
    user_id        TEXT,
    ticker         TEXT,
    trigger_price  NUMERIC(19,8),
    created_at     BIGINT,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS sandbox_price_alerts (
    id             TEXT PRIMARY KEY,
    user_id        TEXT,
    ticker         TEXT,
    target_price   NUMERIC(19,8),
    above          BOOLEAN,
    created_at     BIGINT,
    schema_version INTEGER
);

CREATE TABLE IF NOT EXISTS dca_orders (
    id             BIGSERIAL PRIMARY KEY,
    user_id        TEXT NOT NULL,
    ticker         TEXT NOT NULL,
    amount_rub     NUMERIC(19,8) NOT NULL,
    frequency      TEXT NOT NULL,
    next_execution BIGINT NOT NULL,
    created_at     BIGINT NOT NULL,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (user_id, ticker)
);

-- Деньги хранились как DOUBLE PRECISION, из-за чего копейки дрейфовали при
-- накоплении (баланс, средняя цена, комиссии). Перевод в NUMERIC — точный тип.
-- Даты хранились строками: сравнение и сортировка были возможны только в Java.
-- Приведение идемпотентно: повторный ALTER на колонке нужного типа ничего не меняет.
ALTER TABLE sandbox_users ALTER COLUMN cash                    TYPE NUMERIC(19,8) USING cash::numeric;
ALTER TABLE sandbox_users ALTER COLUMN borrowed                TYPE NUMERIC(19,8) USING borrowed::numeric;
ALTER TABLE sandbox_users ALTER COLUMN total_fees              TYPE NUMERIC(19,8) USING total_fees::numeric;
ALTER TABLE sandbox_users ALTER COLUMN daily_baseline_equity   TYPE NUMERIC(19,8) USING daily_baseline_equity::numeric;
ALTER TABLE sandbox_users ALTER COLUMN weekly_baseline_equity  TYPE NUMERIC(19,8) USING weekly_baseline_equity::numeric;
ALTER TABLE sandbox_users ALTER COLUMN monthly_baseline_equity TYPE NUMERIC(19,8) USING monthly_baseline_equity::numeric;
ALTER TABLE sandbox_users ALTER COLUMN daily_baseline_date     TYPE DATE USING daily_baseline_date::date;
ALTER TABLE sandbox_users ALTER COLUMN weekly_baseline_date    TYPE DATE USING weekly_baseline_date::date;
ALTER TABLE sandbox_users ALTER COLUMN monthly_baseline_date   TYPE DATE USING monthly_baseline_date::date;
ALTER TABLE sandbox_users ALTER COLUMN last_replenish_date     TYPE DATE USING last_replenish_date::date;
ALTER TABLE sandbox_positions   ALTER COLUMN avg_price     TYPE NUMERIC(19,8) USING avg_price::numeric;
ALTER TABLE sandbox_trades      ALTER COLUMN price         TYPE NUMERIC(19,8) USING price::numeric;
ALTER TABLE sandbox_trades      ALTER COLUMN fee           TYPE NUMERIC(19,8) USING fee::numeric;
ALTER TABLE sandbox_limit_orders ALTER COLUMN limit_price  TYPE NUMERIC(19,8) USING limit_price::numeric;
ALTER TABLE sandbox_stop_orders  ALTER COLUMN trigger_price TYPE NUMERIC(19,8) USING trigger_price::numeric;
ALTER TABLE sandbox_price_alerts ALTER COLUMN target_price  TYPE NUMERIC(19,8) USING target_price::numeric;
ALTER TABLE dca_orders           ALTER COLUMN amount_rub    TYPE NUMERIC(19,8) USING amount_rub::numeric;
