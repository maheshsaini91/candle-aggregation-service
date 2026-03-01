-- Initial seed: symbols and base_price (no code seeding; all from SQL).
ALTER TABLE symbols ADD COLUMN IF NOT EXISTS base_price DOUBLE PRECISION NOT NULL DEFAULT 100;

INSERT INTO symbols (symbol, status, base_price) VALUES
    ('BTC-USD',  'ACTIVE', 65000),
    ('ETH-USD',  'ACTIVE', 3500),
    ('SOL-USD',  'ACTIVE', 150),
    ('DOGE-USD', 'ACTIVE', 0.15)
ON CONFLICT (symbol) DO NOTHING;
