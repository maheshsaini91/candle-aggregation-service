-- Finalized candles: one row per (symbol, interval_sec, bucket_start).
-- Runs automatically on first PostgreSQL container start (docker-entrypoint-initdb.d).
CREATE TABLE IF NOT EXISTS candles (
    symbol       VARCHAR(32) NOT NULL,
    interval_sec BIGINT      NOT NULL,
    bucket_start BIGINT      NOT NULL,
    open         DOUBLE PRECISION NOT NULL,
    high         DOUBLE PRECISION NOT NULL,
    low          DOUBLE PRECISION NOT NULL,
    close        DOUBLE PRECISION NOT NULL,
    volume       BIGINT      NOT NULL,
    PRIMARY KEY (symbol, interval_sec, bucket_start)
);

CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_bucket
    ON candles (symbol, interval_sec, bucket_start);

-- Symbols table: symbol, status, base_price for generator.
CREATE TABLE IF NOT EXISTS symbols (
    id         BIGSERIAL PRIMARY KEY,
    symbol     VARCHAR(32) NOT NULL UNIQUE,
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    base_price DOUBLE PRECISION NOT NULL DEFAULT 100
);

CREATE INDEX IF NOT EXISTS idx_symbols_status ON symbols (status);
CREATE INDEX IF NOT EXISTS idx_symbols_symbol ON symbols (symbol);
