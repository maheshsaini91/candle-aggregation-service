package com.multibank.aggregationservice.repositories;

import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;

import java.util.List;

/**
 * Abstraction over candle storage (Repository pattern).
 * Allows swapping in-memory implementation for a time-series DB (e.g. PostgreSQL/TimescaleDB)
 * without changing aggregation or API logic.
 */
public interface CandleRepository {

    void updateActiveCandle(CandleKey key, double price);

    void finalizeCandle(CandleKey key);

    List<Candle> findFinalized(String symbol, long intervalSec, long from, long to);

    Candle getActiveSnapshot(CandleKey key);

    void flushAllActive();

    int activeCandleCount();

    int finalizedCandleCount();
}
