package com.multibank.aggregationservice;

import com.multibank.aggregationservice.enums.Interval;
import com.multibank.aggregationservice.models.BidAskEvent;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.repositories.CandleRepository;
import com.multibank.aggregationservice.services.CandleAggregationService;
import com.multibank.aggregationservice.services.CandleWindowManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentAggregationTest {

    private CandleRepository repository;
    private CandleWindowManager windowManager;
    private CandleAggregationService aggregationService;

    private static final long BASE_TS = 1620000000L;
    private static final String SYMBOL = "BTC-USD";

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        repository          = new CandleRepository(metrics);
        windowManager       = new CandleWindowManager(repository);
        aggregationService  = new CandleAggregationService(repository, windowManager, metrics);
    }

    // -----------------------------------------------------------------------
    // Core concurrency proof: volume must equal exact event count
    // -----------------------------------------------------------------------

    @RepeatedTest(3)
    @DisplayName("Volume exactly equals total events sent from N concurrent threads")
    void volumeIsExactUnderConcurrentLoad() throws InterruptedException {
        int threadCount     = 20;
        int eventsPerThread = 500;
        int totalEvents     = threadCount * eventsPerThread;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService pool     = Executors.newFixedThreadPool(threadCount);

        // All threads hammer the same symbol/bucket simultaneously
        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    startGate.await(); // synchronised start for maximum contention
                    for (int i = 0; i < eventsPerThread; i++) {
                        // All events in same bucket — tests concurrent update() on MutableCandle
                        aggregationService.processInternal(
                                new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + i));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        doneLatch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Force window close
        aggregationService.processInternal(
                new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 3600));

        CandleKey key = new CandleKey(SYMBOL, Interval.ONE_HOUR.getSeconds(), BASE_TS);
        var candle = repository.findFinalized(SYMBOL, Interval.ONE_HOUR.getSeconds(),
                BASE_TS, BASE_TS + 3599);

        assertThat(candle).hasSize(1);
        assertThat(candle.get(0).volume())
                .as("Volume must exactly equal total events — any shortfall indicates a lost update")
                .isEqualTo(totalEvents);
    }

    // -----------------------------------------------------------------------
    // OHLC invariants must hold under concurrent writes
    // -----------------------------------------------------------------------

    @RepeatedTest(3)
    @DisplayName("OHLC invariants hold under concurrent writes: high >= open >= low, high >= close >= low")
    void ohlcInvariantsHoldUnderConcurrentLoad() throws InterruptedException {
        int threadCount     = 10;
        int eventsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done  = new CountDownLatch(threadCount);

        // Threads send varied prices to stress high/low tracking
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < eventsPerThread; i++) {
                        double price = 29000.0 + (threadId * 100) + (i % 50);
                        aggregationService.processInternal(
                                new BidAskEvent(SYMBOL, price - 5, price + 5, BASE_TS + i));
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Snapshot the last active candle (earlier buckets are finalized when we advance)
        // Events use BASE_TS + 0..199; for 1m interval the last bucket is BASE_TS + 180
        long lastBucket = BASE_TS + 180;
        CandleKey key = new CandleKey(SYMBOL, Interval.ONE_MIN.getSeconds(), lastBucket);
        var snapshot = repository.getActiveSnapshot(key);

        assertThat(snapshot)
                .as("Last 1m bucket (BASE_TS+180) should still be active")
                .isNotNull();
        assertThat(snapshot.high())
                .as("High must be >= Open")
                .isGreaterThanOrEqualTo(snapshot.open());
        assertThat(snapshot.low())
                .as("Low must be <= Open")
                .isLessThanOrEqualTo(snapshot.open());
        assertThat(snapshot.high())
                .as("High must be >= Close")
                .isGreaterThanOrEqualTo(snapshot.close());
        assertThat(snapshot.low())
                .as("Low must be <= Close")
                .isLessThanOrEqualTo(snapshot.close());
        assertThat(snapshot.high())
                .as("High must be >= Low (fundamental OHLC invariant)")
                .isGreaterThanOrEqualTo(snapshot.low());
    }

    // -----------------------------------------------------------------------
    // Multiple symbols: no cross-symbol interference
    // -----------------------------------------------------------------------

    @RepeatedTest(3)
    @DisplayName("Concurrent writes to different symbols do not interfere with each other")
    void multipleSymbolsAreFullyIsolated() throws InterruptedException {
        List<String> symbols = List.of("BTC-USD", "ETH-USD", "SOL-USD", "DOGE-USD");
        int eventsPerSymbol  = 300;
        ExecutorService pool = Executors.newFixedThreadPool(symbols.size());
        CountDownLatch done  = new CountDownLatch(symbols.size());

        // Each symbol gets exactly eventsPerSymbol events from its own thread
        for (String symbol : symbols) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < eventsPerSymbol; i++) {
                        aggregationService.processInternal(
                                new BidAskEvent(symbol, 100.0, 102.0, BASE_TS + i));
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Close all windows
        for (String symbol : symbols) {
            aggregationService.processInternal(
                    new BidAskEvent(symbol, 100.0, 102.0, BASE_TS + 3600));
        }

        // Each symbol should have exactly eventsPerSymbol ticks in volume
        for (String symbol : symbols) {
            var candles = repository.findFinalized(
                    symbol, Interval.ONE_HOUR.getSeconds(), BASE_TS, BASE_TS + 3599);
            assertThat(candles).hasSize(1);
            assertThat(candles.get(0).volume())
                    .as("Symbol %s should have exactly %d events", symbol, eventsPerSymbol)
                    .isEqualTo(eventsPerSymbol);
        }
    }

    // -----------------------------------------------------------------------
    // Concurrent reads and writes must not deadlock or produce stale reads
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Concurrent reads (getHistory) and writes (processInternal) do not deadlock")
    void concurrentReadsAndWritesDoNotDeadlock() throws InterruptedException {
        int writerThreads = 5;
        int readerThreads = 5;
        int durationMs    = 3000;

        ExecutorService pool     = Executors.newFixedThreadPool(writerThreads + readerThreads);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger readCount  = new AtomicInteger(0);
        AtomicInteger errors     = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // Writers: continuously send events
        for (int i = 0; i < writerThreads; i++) {
            futures.add(pool.submit(() -> {
                long end = System.currentTimeMillis() + durationMs;
                while (System.currentTimeMillis() < end) {
                    try {
                        aggregationService.processInternal(
                                new BidAskEvent(SYMBOL, 29500.0, 29510.0,
                                        System.currentTimeMillis() / 1000));
                        writeCount.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            }));
        }

        // Readers: continuously call getHistory
        for (int i = 0; i < readerThreads; i++) {
            futures.add(pool.submit(() -> {
                long end = System.currentTimeMillis() + durationMs;
                while (System.currentTimeMillis() < end) {
                    try {
                        aggregationService.getHistory(SYMBOL, "1m", BASE_TS, BASE_TS + 600);
                        readCount.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        // Expected for valid intervals
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            }));
        }

        pool.shutdown();
        boolean completed = pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(completed).as("Thread pool should complete without timeout — possible deadlock").isTrue();
        assertThat(errors.get()).as("No errors should occur during concurrent read/write").isEqualTo(0);
        assertThat(writeCount.get()).as("Writers should have made progress").isPositive();
        assertThat(readCount.get()).as("Readers should have made progress").isPositive();
    }
}
