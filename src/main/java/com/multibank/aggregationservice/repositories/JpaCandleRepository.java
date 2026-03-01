package com.multibank.aggregationservice.repositories;

import com.multibank.aggregationservice.entities.CandleEntity;
import com.multibank.aggregationservice.entities.CandleId;
import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.models.MutableCandle;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@Profile("db")
public class JpaCandleRepository implements CandleRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaCandleRepository.class);

    private final CandleJpaRepository candleJpaRepository;
    private final Counter candlesFinalized;

    private final ConcurrentMap<CandleKey, MutableCandle> activeCandles = new ConcurrentHashMap<>();

    public JpaCandleRepository(CandleJpaRepository candleJpaRepository, MeterRegistry meterRegistry) {
        this.candleJpaRepository = candleJpaRepository;
        this.candlesFinalized = Counter.builder("candle.finalized.total")
                .description("Total candle windows finalized since startup")
                .register(meterRegistry);
    }

    @Override
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

    @Override
    @Transactional
    public void finalizeCandle(CandleKey key) {
        MutableCandle active = activeCandles.get(key);
        if (active == null) {
            return;
        }
        Candle snapshot = active.toCandle();

        CandleId id = new CandleId(key.symbol(), key.intervalSec(), key.bucketStart());
        CandleEntity entity = candleJpaRepository.findById(id).orElse(new CandleEntity());
        entity.setId(id);
        entity.setOpen(snapshot.open());
        entity.setHigh(snapshot.high());
        entity.setLow(snapshot.low());
        entity.setClose(snapshot.close());
        entity.setVolume(snapshot.volume());
        candleJpaRepository.save(entity);
        candlesFinalized.increment();

        activeCandles.remove(key);

        log.info("Candle finalized: symbol={} interval={}s t={} O={} H={} L={} C={} V={}",
                key.symbol(), key.intervalSec(), snapshot.time(),
                String.format("%.4f", snapshot.open()),
                String.format("%.4f", snapshot.high()),
                String.format("%.4f", snapshot.low()),
                String.format("%.4f", snapshot.close()),
                snapshot.volume());
    }

    @Override
    public List<Candle> findFinalized(String symbol, long intervalSec, long from, long to) {
        long alignedFrom = (from / intervalSec) * intervalSec;
        return candleJpaRepository.findFinalizedRange(symbol, intervalSec, alignedFrom, to);
    }

    @Override
    public Candle getActiveSnapshot(CandleKey key) {
        MutableCandle active = activeCandles.get(key);
        return active != null ? active.toCandle() : null;
    }

    @Override
    public void flushAllActive() {
        log.info("Flushing {} active candles", activeCandles.size());
        new ArrayList<>(activeCandles.keySet()).forEach(this::finalizeCandle);
    }

    @Override
    public int activeCandleCount() {
        return activeCandles.size();
    }

    @Override
    public int finalizedCandleCount() {
        return (int) candleJpaRepository.count();
    }
}
