package com.multibank.aggregationservice.services;

import com.multibank.aggregationservice.enums.Interval;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.repositories.CandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CandleWindowManager {
    private static final Logger log = LoggerFactory.getLogger(CandleWindowManager.class);
    private final CandleRepository repository;
    private final ConcurrentMap<String, Long> lastBucketPerKey = new ConcurrentHashMap<>();

    public CandleWindowManager(CandleRepository repository) {
        this.repository = repository;
    }

    public long resolveBucket(long eventTimestampSec, Interval interval) {
        return (eventTimestampSec / interval.getSeconds()) * interval.getSeconds();
    }

    public void checkAndFinalizeIfWindowClosed(String symbol, Interval interval, long newBucket) {
        String trackingKey = symbol + "::" + interval.getSeconds();
        long intervalSec = interval.getSeconds();

        Long previousBucket = lastBucketPerKey.get(trackingKey);

        if (previousBucket != null && newBucket > previousBucket) {
            // One or more windows closed — finalize every closed bucket (handles event gaps)
            for (long b = previousBucket; b < newBucket; b += intervalSec) {
                CandleKey oldKey = new CandleKey(symbol, intervalSec, b);
                log.debug("Window closed: symbol={} interval={}s bucket={}",
                        symbol, intervalSec, b);
                repository.finalizeCandle(oldKey);
            }
        }
        // Only advance lastBucket; never downgrade on late events (out-of-order delivery)
        lastBucketPerKey.merge(trackingKey, newBucket, (a, b) -> Math.max(a, b));
    }
}
