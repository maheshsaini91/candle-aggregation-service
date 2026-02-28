package com.multibank.aggregationservice.services;

import com.multibank.aggregationservice.models.BidAskEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SymbolEventQueue implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(SymbolEventQueue.class);
    private final String symbol;
    private final BlockingQueue<BidAskEvent> queue;
    private final CandleAggregationService aggregationService;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Metrics
    private final Counter eventsQueued;
    private final Counter eventsDropped;
    private final Counter eventsProcessed;

    public SymbolEventQueue(
            String symbol,
            int capacity,
            CandleAggregationService aggregationService,
            MeterRegistry meterRegistry
    ) {
        this.symbol             = symbol;
        this.queue              = new LinkedBlockingQueue<>(capacity);
        this.aggregationService = aggregationService;

        // Tag all metrics by symbol for per-symbol observability in Prometheus/Grafana
        this.eventsQueued    = Counter.builder("candle.events.queued")
                .tag("symbol", symbol)
                .description("Total events accepted into the symbol queue")
                .register(meterRegistry);

        this.eventsDropped   = Counter.builder("candle.events.dropped")
                .tag("symbol", symbol)
                .description("Events dropped due to full queue (backpressure) or late arrival")
                .register(meterRegistry);

        this.eventsProcessed = Counter.builder("candle.events.processed")
                .tag("symbol", symbol)
                .description("Total events successfully aggregated")
                .register(meterRegistry);
    }

    public void offer(BidAskEvent event) {
        boolean accepted = queue.offer(event);
        if (accepted) {
            eventsQueued.increment();
        } else {
            eventsDropped.increment();
            log.warn("Queue full for symbol={} — dropping event ts={}. " +
                    "Consider increasing candle.aggregation.queue-capacity or " +
                    "scaling out aggregation.", symbol, event.timestamp());
        }
    }


    @Override
    public void run() {
        log.info("Aggregation consumer started for symbol={}", symbol);
        while (running.get() || ! queue.isEmpty()) {
            try {
                BidAskEvent event = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    aggregationService.processInternal(event);
                    eventsProcessed.increment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Consumer thread interrupted for symbol={}", symbol);
                break;
            } catch (Exception e) {
                // Log and continue — one bad event must not kill the consumer
                log.error("Error processing event for symbol={}: {}", symbol, e.getMessage(), e);
            }
        }
        log.info("Aggregation consumer stopped for symbol={}", symbol);
    }

    public void stop() {
        running.set(false);
    }

    public int queueSize() {
        return queue.size();
    }

    public String getSymbol() {
        return symbol;
    }
}
