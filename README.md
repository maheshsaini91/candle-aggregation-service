# Candle Aggregation Service

A backend Java service that consumes a stream of bid/ask market data, aggregates it into OHLC (candlestick) format per symbol and interval, and exposes a history API for frontend charting (e.g. TradingView Lightweight Charts).

## Project Overview

- **Stream ingestion**: Accepts `BidAskEvent(symbol, bid, ask, timestamp)` from a scheduled generator, or can be wired to Kafka/WebSocket.
- **Candlestick aggregation**: OHLC candles per **(symbol, interval)** with wall-clock-aligned buckets. Intervals: `1s`, `5s`, `1m`, `15m`, `1h`. Volume is tick count per window.
- **Storage**: **PostgreSQL** by default (profile `db`). Run with Docker: `./run.sh` or `docker compose up --build` — no Java/Maven on host. `CandleRepository` is an interface (JPA implementation for DB; in-memory when not using `db`).
- **History API**: `GET /v1/history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620000600` returns TradingView-style `s`, `t`, `o`, `h`, `l`, `c`, `v` (UNIX timestamps).
- **Non-functional**: Thread-safe aggregation, per-symbol queues with backpressure, graceful shutdown with candle flush, health and Prometheus metrics, late-event grace period.

## Assumptions and Trade-offs

- **Mid-price for OHLC**: OHLC values are derived from mid price `(bid + ask) / 2`; bid/ask are not stored separately per tick.
- **Volume**: Volume is the number of ticks (events) in the window; not quote or notional volume.
- **Late events**: Events older than a configurable grace period (default 5s) are dropped; no out-of-order replay for very old data.
- **Context path**: `server.servlet.context-path=/aggregationservice/api` — history at `/aggregationservice/api/v1/history`.
- **Range limit**: History requests are limited to a 7-day range to avoid unbounded responses.

## How to Run

### Run on macOS (minimal — everything in Docker)

No Java or Maven on your machine. Only Docker + Docker Compose.

**Colima works on all macOS versions** (Ventura, Sonoma, Monterey, etc.). Docker Desktop requires macOS 14 (Sonoma) or newer.

**One-time setup:**

```bash
# 1. Xcode CLI (if needed)
xcode-select --install

# 2. Homebrew — https://brew.sh

# 3. Docker + Compose + jq (Colima runs on every macOS version)
brew install colima docker docker-compose jq

# 4. Start Docker
colima start
```

**Run the app** (builds app image, starts Postgres, starts app):

```bash
cd /path/to/aggregationservice
./run.sh
```

Or without the script: `docker compose up --build`

First run may take a few minutes (image build). App: http://localhost:8080/aggregationservice/api/actuator/health

**Next times:** `colima start` (if stopped), then `./run.sh`.

If you see `docker-credential-desktop: executable file not found`, install jq (`brew install jq`); the script will fix the Docker config. Or edit `~/.docker/config.json` and remove the `credsStore` line.

---

### Commands summary

| What | Command |
|------|--------|
| **Run (all in Docker)** | `./run.sh` or `docker compose up --build` |
| **Run in background** | `docker compose up -d --build` |
| **Build (local)** | `mvn -q clean package` |
| **Run app on host** | `./run-local-with-docker-db.sh` *(Postgres in Docker, app via Maven)* |
| **Tests** | `mvn -q test` |

- **Config**: `src/main/resources/application.properties` — generator rate, queue capacity, grace period. Symbols and base prices come from DB only (seeded via SQL). App runs on port 8080.

### Optional: run app on host (Postgres in Docker)

If you have Java/Maven and want to run the app locally with Postgres in Docker:

```bash
./run-local-with-docker-db.sh
```

Or: `docker compose up -d postgres` then `mvn -q spring-boot:run -DskipTests`.

- Postgres runs migrations from `docker/postgres/init/` on first start.
- App container uses **profile db** and credentials from `.env`.

**Adding migrations:** Put SQL files in `docker/postgres/init/` with names like `01_schema.sql`, `02_add_foo.sql`. They run in alphabetical order on **first** container start (when the data volume is empty). For an existing DB, run new scripts manually or recreate the volume.

**Manual Postgres (no Docker)**

1. Create a database (e.g. `aggregation`) and run `src/main/resources/schema.sql` once (or let the app apply it on startup via `spring.sql.init.mode=always`).
2. Set DB URL, username, and password (e.g. in `application.properties` or env).
3. Run the app: `mvn spring-boot:run`

## Running Tests

```bash
mvn -q test
```

By default, **integration** tests (tag `integration`) are **excluded** so `mvn test` does not require Docker. To run the PostgreSQL-backed repository integration test (Testcontainers):

```bash
mvn test -Dtest=CandleRepositoryIntegrationTest
```
(Docker must be running.)

If you see Mockito `MockMaker` initialization errors (e.g. in some CI or restricted environments), run the core unit tests only:

```bash
mvn -q test -Dtest=CandleAggregationServiceTest,CandleRepositoryTest,CandleWindowManagerTest,LateEventHandlingTest,ConcurrentAggregationTest
```

Tests include:

- **CandleAggregationServiceTest**: OHLC logic, mid-price, volume, multiple symbols/intervals, unsupported interval.
- **CandleRepositoryTest**: Active/finalized storage, range queries, flush.
- **CandleWindowManagerTest**: Bucket alignment (1s, 5s, 1m, 1h), finalization on rollover, skipped-bucket finalization.
- **HistoryControllerTest**: GET /v1/history response shape, validation (from > to, range limit, blank symbol, bad interval), health and metrics.
- **LateEventHandlingTest**: Grace-period acceptance and late-event drop.
- **ConcurrentAggregationTest**: Volume correctness and OHLC invariants under concurrent load, multi-symbol isolation, no deadlock under read/write mix.
- **CandleRepositoryIntegrationTest** (tag `integration`, requires Docker): Finalize → DB write, idempotent finalize, `findFinalized` range, `flushAllActive`, `finalizedCandleCount` against real PostgreSQL (JPA).

## API Summary

| Method | Path        | Query params                    | Description |
|--------|-------------|----------------------------------|-------------|
| GET    | /v1/history | symbol, interval, from, to      | Historical candles (TradingView-style `s`, `t`, `o`, `h`, `l`, `c`, `v`). |

Example:

```bash
curl "http://localhost:8080/aggregationservice/api/v1/history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620003600"
```

## Observability

- **Health**: `GET /aggregationservice/api/actuator/health`
- **Metrics**: `GET /aggregationservice/api/actuator/prometheus` — `candle.*` counters and gauges (events queued/dropped/processed, queue depth, finalized count, history response time).

## Bonus / Extensibility

- **JSON structured logging**: Logs are emitted in JSON format (Logback with Logstash encoder) for readability and easy parsing in log aggregators (e.g. ELK, Splunk).
- **Micrometer metrics**: Per-symbol and global candle metrics for production monitoring.
- **Per-symbol consumer threads**: Dedicated queue and consumer per symbol; queue capacity and grace period configurable.
- **Skipped-bucket finalization**: When events jump forward in time, all intermediate closed buckets are finalized so history stays consistent.
- **Late-event handling**: Configurable grace period; late events dropped with logging.
- **Clean separation**: Aggregation is independent of ingestion; add Kafka/WebSocket by implementing a producer that calls `IngestionService.ingest(BidAskEvent)`.
- **DB repository**: `JpaCandleRepository` (profile `db`) stores finalized candles in PostgreSQL; actives in memory. Idempotent save for safe retries.
