package com.multibank.aggregationservice.repositories;

import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.models.MutableCandle;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class CandleRepository {

    private static final Logger log = LoggerFactory.getLogger(CandleRepository.class);

    private final ConcurrentMap<CandleKey, MutableCandle> activeCandles = new ConcurrentHashMap<>();
    private final ConcurrentMap<CandleKey, Candle> finalizedCandles = new ConcurrentHashMap<>();
    private final Counter candlesFinalized;

    public CandleRepository(MeterRegistry meterRegistry) {
        this.candlesFinalized = Counter.builder("candle.finalized.total")
                .description("Total candle windows finalized since startup")
                .register(meterRegistry);
    }

    public void updateActiveCandle(CandleKey key, double price) {
        activeCandles.compute(key, (k, existing) -> {
            if (existing == null) {
                log.debug("New candle window: symbol={} interval={}s bucket={}",
                        k.symbol(), k.intervalSec(), k.bucketStart());
                return new MutableCandle(k.bucketStart(), price);
            }
            existing.update(price);
            return existing;
        });
    }

    public void finalizeCandle(CandleKey key) {
        MutableCandle active = activeCandles.remove(key);
        if (active != null) {
            Candle finalized = active.toCandle();
            finalizedCandles.put(key, finalized);
            candlesFinalized.increment();
            log.info("Candle finalized: symbol={} interval={}s t={} O={} H={} L={} C={} V={}",
                    key.symbol(), key.intervalSec(), finalized.time(),
                    String.format("%.4f", finalized.open()),
                    String.format("%.4f", finalized.high()),
                    String.format("%.4f", finalized.low()),
                    String.format("%.4f", finalized.close()),
                    finalized.volume());
        }
    }

    public List<Candle> findFinalized(String symbol, long intervalSec, long from, long to) {
        List<Candle> result = new ArrayList<>();
        long alignedFrom = (from / intervalSec) * intervalSec;

        for (long bucket = alignedFrom; bucket <= to; bucket += intervalSec) {
            CandleKey key = new CandleKey(symbol, intervalSec, bucket);
            Candle candle = finalizedCandles.get(key);
            if (candle != null) {
                result.add(candle);
            }
        }
        return result;
    }

    public Candle getActiveSnapshot(CandleKey key) {
        MutableCandle active = activeCandles.get(key);
        return active != null ? active.toCandle() : null;
    }

    public void flushAllActive() {
        log.info("Flushing {} active candles", activeCandles.size());
        new ArrayList<>(activeCandles.keySet()).forEach(this::finalizeCandle);
    }

    public int activeCandleCount()    { return activeCandles.size(); }
    public int finalizedCandleCount() { return finalizedCandles.size(); }
}
