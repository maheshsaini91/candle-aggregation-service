# Candle Aggregation Service

A backend Java service that consumes a stream of bid/ask market data, aggregates it into OHLC (candlestick) format per symbol and interval, and exposes a history API for frontend charting (e.g. TradingView Lightweight Charts).

## Project Overview

- **Stream ingestion**: Accepts `BidAskEvent(symbol, bid, ask, timestamp)` from a simulated generator (scheduled), or can be wired to Kafka/WebSocket.
- **Candlestick aggregation**: Builds OHLC candles per **(symbol, interval)** with wall-clock-aligned buckets. Intervals: `1s`, `5s`, `1m`, `15m`, `1h`. Volume is tick count (number of events per window).
- **Storage**: In-memory `ConcurrentMap`-based repository (active + finalized candles). The **Repository pattern** is used: `CandleRepository` is an interface implemented by `InMemoryCandleRepository`, so persistence can be swapped for PostgreSQL/TimescaleDB without changing aggregation or API code.
- **History API**: `GET /history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620000600` returns TradingView-style arrays: `s`, `t`, `o`, `h`, `l`, `c`, `v` (timestamps in UNIX seconds).
- **Non-functional**: Thread-safe aggregation, per-symbol queues with backpressure, graceful shutdown with candle flush, health and Prometheus metrics, late-event grace period.

## Assumptions and Trade-offs

- **Mid-price for OHLC**: OHLC values are derived from mid price `(bid + ask) / 2`; bid/ask are not stored separately per tick.
- **Volume**: Volume is the number of ticks (events) in the window; not quote or notional volume.
- **Late events**: Events older than a configurable grace period (default 5s) are dropped to avoid skewing recent candles; no out-of-order replay for very old data.
- **In-memory storage**: No persistence across restarts; suitable for demo and development. For production, a time-series store (e.g. TimescaleDB) is recommended.
- **Context path**: Default deployment uses `server.servlet.context-path=/aggregationservice/api`, so the history endpoint is `/aggregationservice/api/history` (or `/aggregationservice/api/v1/history`). Without context path, `GET /history` and `GET /v1/history` both work.
- **Range limit**: History requests are limited to a 7-day range to avoid unbounded responses.

## How to Run

- **Build**: `mvn -q clean package`
- **Run**: `mvn -q spring-boot:run` (or run `AggregationserviceApplication` from your IDE).
- **Config**: See `src/main/resources/application.properties` for:
  - `candle.generator.enabled`, `candle.generator.rate-ms`, `candle.symbols`, `candle.base-prices`
  - `candle.aggregation.queue-capacity`, `candle.aggregation.grace-period-sec`

With default config, the app runs on port 8080, generates simulated bid/ask events every 200 ms for BTC-USD, ETH-USD, SOL-USD, and serves history and actuator endpoints.

## Running Tests

```bash
mvn -q test
```

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

## API Summary

| Method | Path        | Query params                    | Description |
|--------|-------------|----------------------------------|-------------|
| GET    | /history    | symbol, interval, from, to      | Historical candles (TradingView-style `s,t,o,h,l,c,v`). Also at `/v1/history`. |

Example:

```bash
curl "http://localhost:8080/aggregationservice/api/history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620000600"
```

## Observability

- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/prometheus` (or `/actuator/metrics`) — includes `candle.*` counters and gauges (events queued/dropped/processed, queue depth, finalized count, history response time).

## Bonus / Extensibility

- **Micrometer metrics**: Per-symbol and global candle metrics for production monitoring.
- **Per-symbol consumer threads**: Dedicated queue and consumer per symbol to avoid head-of-line blocking; queue capacity and grace period are configurable.
- **Skipped-bucket finalization**: When events jump forward in time (e.g. after a gap), all intermediate closed buckets are finalized so history stays consistent.
- **Late-event handling**: Configurable grace period; late events are dropped with logging and no crash.
- **Clean separation**: Aggregation logic is independent of ingestion source; swapping in Kafka or WebSocket only requires a new event producer that calls `IngestionService.ingest(BidAskEvent)`.
