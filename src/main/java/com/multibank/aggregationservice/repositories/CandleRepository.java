package com.multibank.aggregationservice.repositories;

import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;

import java.util.List;

public interface CandleRepository {

    void updateActiveCandle(CandleKey key, double price);

    void finalizeCandle(CandleKey key);

    List<Candle> findFinalized(String symbol, long intervalSec, long from, long to);

    Candle getActiveSnapshot(CandleKey key);

    void flushAllActive();

    int activeCandleCount();

    int finalizedCandleCount();
}
