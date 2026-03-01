package com.multibank.aggregationservice.services;

import com.multibank.aggregationservice.models.BidAskEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final CandleAggregationService aggregationService;
    private final MeterRegistry meterRegistry;
    private final int queueCapacity;
    private final long gracePeriodSec;

    private final Map<String, SymbolEventQueue> queues = new ConcurrentHashMap<>();
    private final ExecutorService consumerPool;

    public IngestionService(
            CandleAggregationService aggregationService,
            MeterRegistry meterRegistry,
            @Value("${candle.aggregation.queue-capacity:10000}") int queueCapacity,
            @Value("${candle.aggregation.grace-period-sec:5}") long gracePeriodSec
    ) {
        this.aggregationService = aggregationService;
        this.meterRegistry      = meterRegistry;
        this.queueCapacity      = queueCapacity;
        this.gracePeriodSec     = gracePeriodSec;

        this.consumerPool = Executors.newCachedThreadPool();

        Gauge.builder("candle.queue.total_depth", queues,
                        m -> m.values().stream().mapToInt(SymbolEventQueue::queueSize).sum())
                .description("Total events waiting across all symbol queues")
                .register(meterRegistry);
    }

    public void ingest(BidAskEvent event) {
        if (isLate(event)) {
            log.warn("Dropping late event: symbol={} eventTs={} lag={}s (gracePeriod={}s)",
                    event.symbol(),
                    event.timestamp(),
                    Instant.now().getEpochSecond() - event.timestamp(),
                    gracePeriodSec);
            return;
        }

        SymbolEventQueue queue = queues.computeIfAbsent(event.symbol(), this::createQueue);
        queue.offer(event);
    }

    private boolean isLate(BidAskEvent event) {
        long now = Instant.now().getEpochSecond();
        return (now - event.timestamp()) > gracePeriodSec;
    }

    private SymbolEventQueue createQueue(String symbol) {
        log.info("Initialising event queue and consumer for symbol={} capacity={}",
                symbol, queueCapacity);
        SymbolEventQueue q = new SymbolEventQueue(
                symbol, queueCapacity, aggregationService, meterRegistry);

        consumerPool.submit(q);

        Gauge.builder("candle.queue.depth", q, SymbolEventQueue::queueSize)
                .tag("symbol", symbol)
                .description("Events waiting in queue for this symbol")
                .register(meterRegistry);

        return q;
    }

    @PreDestroy
    public void shutdown() {
        log.info("IngestionService shutting down — draining {} symbol queues", queues.size());
        queues.values().forEach(SymbolEventQueue::stop);

        consumerPool.shutdown();
        try {
            if (!consumerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Consumer pool did not terminate in 30s — forcing shutdown");
                consumerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            consumerPool.shutdownNow();
        }
        log.info("IngestionService shutdown complete");
    }

    public List<String> activeSymbols() {
        return List.copyOf(queues.keySet());
    }

    public int queueDepth(String symbol) {
        SymbolEventQueue q = queues.get(symbol);
        return q != null ? q.queueSize() : 0;
    }
}
