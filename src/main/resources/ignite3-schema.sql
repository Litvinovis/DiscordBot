-- Apache Ignite 3.x DDL schema for Stonks Bot sandbox
-- All column names are unquoted (Ignite 3 returns uppercase in ResultSet)
-- Reserved SQL words avoided: 'type' -> order_type, 'side' -> trade_side

CREATE TABLE IF NOT EXISTS sandbox_users (
  user_id VARCHAR PRIMARY KEY,
  user_name VARCHAR,
  cash DOUBLE,
  borrowed DOUBLE,
  total_fees DOUBLE,
  daily_baseline_date VARCHAR,
  daily_baseline_equity DOUBLE,
  weekly_baseline_date VARCHAR,
  weekly_baseline_equity DOUBLE,
  monthly_baseline_date VARCHAR,
  monthly_baseline_equity DOUBLE,
  currency_holdings VARCHAR,
  schema_version INT
);

CREATE TABLE IF NOT EXISTS sandbox_positions (
  position_key VARCHAR PRIMARY KEY,
  user_id VARCHAR,
  ticker VARCHAR,
  instrument_id VARCHAR,
  quantity INT,
  avg_price DOUBLE,
  schema_version INT
);

CREATE TABLE IF NOT EXISTS sandbox_trades (
  id VARCHAR PRIMARY KEY,
  user_id VARCHAR,
  ticker VARCHAR,
  trade_side VARCHAR,
  qty INT,
  price DOUBLE,
  fee DOUBLE,
  trade_timestamp BIGINT,
  schema_version INT
);

CREATE TABLE IF NOT EXISTS sandbox_limit_orders (
  id VARCHAR PRIMARY KEY,
  user_id VARCHAR,
  user_name VARCHAR,
  ticker VARCHAR,
  trade_side VARCHAR,
  qty INT,
  limit_price DOUBLE,
  created_at BIGINT,
  schema_version INT
);

CREATE TABLE IF NOT EXISTS sandbox_stop_orders (
  id VARCHAR PRIMARY KEY,
  order_type VARCHAR,
  user_id VARCHAR,
  ticker VARCHAR,
  trigger_price DOUBLE,
  created_at BIGINT,
  schema_version INT
);

CREATE TABLE IF NOT EXISTS sandbox_price_alerts (
  id VARCHAR PRIMARY KEY,
  user_id VARCHAR,
  ticker VARCHAR,
  target_price DOUBLE,
  above BOOLEAN,
  created_at BIGINT,
  schema_version INT
);
