package com.multibank.aggregationservice;

import com.multibank.aggregationservice.models.BidAskEvent;
import com.multibank.aggregationservice.repositories.CandleRepository;
import com.multibank.aggregationservice.repositories.InMemoryCandleRepository;
import com.multibank.aggregationservice.services.CandleAggregationService;
import com.multibank.aggregationservice.services.CandleWindowManager;
import com.multibank.aggregationservice.services.IngestionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for late event handling in IngestionService.
 *
 * Late events are a real production problem:
 * - Network jitter can delay events by seconds
 * - Consumer lag in upstream systems (Kafka, WebSocket reconnects)
 * - Clock skew between event producers and this service
 *
 * The grace period is a configurable safety valve. Events within the
 * grace window are accepted. Older events are dropped with a metric.
 */
class LateEventHandlingTest {

    private IngestionService ingestionService;
    private static final long GRACE_PERIOD = 5L;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        CandleRepository repo = new InMemoryCandleRepository(metrics);
        CandleWindowManager windowManager = new CandleWindowManager(repo);
        CandleAggregationService aggregationService =
                new CandleAggregationService(repo, windowManager, metrics);

        ingestionService = new IngestionService(
                aggregationService, metrics, 10000, GRACE_PERIOD);
    }

    @Test
    @DisplayName("Event within grace period is accepted and queued")
    void eventWithinGracePeriodIsAccepted() throws InterruptedException {
        long now = Instant.now().getEpochSecond();
        long recentTs = now - (GRACE_PERIOD - 1); // 1 second inside grace window

        ingestionService.ingest(new BidAskEvent("BTC-USD", 29500.0, 29510.0, recentTs));

        // Give consumer thread time to pick up
        Thread.sleep(200);
        assertThat(ingestionService.activeSymbols()).contains("BTC-USD");
    }

    @Test
    @DisplayName("Event exactly at grace period boundary is accepted")
    void eventAtGracePeriodBoundaryIsAccepted() throws InterruptedException {
        long now = Instant.now().getEpochSecond();
        long boundaryTs = now - GRACE_PERIOD; // exactly at boundary

        // Should be accepted (boundary is inclusive)
        ingestionService.ingest(new BidAskEvent("BTC-USD", 29500.0, 29510.0, boundaryTs));
        Thread.sleep(200);
        assertThat(ingestionService.activeSymbols()).contains("BTC-USD");
    }

    @Test
    @DisplayName("Event beyond grace period is silently dropped")
    void eventBeyondGracePeriodIsDropped() throws InterruptedException {
        long now = Instant.now().getEpochSecond();
        long lateTs = now - (GRACE_PERIOD + 10); // 10 seconds past grace window

        ingestionService.ingest(new BidAskEvent("ETH-USD", 3500.0, 3510.0, lateTs));

        // Wait — no queue should be created since the event was dropped before queuing
        Thread.sleep(200);
        assertThat(ingestionService.activeSymbols()).doesNotContain("ETH-USD");
    }

    @Test
    @DisplayName("Severely late event (minutes old) is dropped without error")
    void severelyLateEventIsDroppedSafely() {
        long tenMinutesAgo = Instant.now().getEpochSecond() - 600;

        // Must not throw — late events are a normal production scenario
        ingestionService.ingest(new BidAskEvent("BTC-USD", 29500.0, 29510.0, tenMinutesAgo));
        // No assertion needed — the point is it doesn't throw
    }

    @Test
    @DisplayName("Fresh events are accepted even when late events are also arriving")
    void freshEventsAcceptedAlongsideLateEvents() throws InterruptedException {
        long now  = Instant.now().getEpochSecond();
        long late = now - (GRACE_PERIOD + 30);

        // Mix of late and fresh events
        ingestionService.ingest(new BidAskEvent("SOL-USD", 150.0, 151.0, late));  // dropped
        ingestionService.ingest(new BidAskEvent("SOL-USD", 150.0, 151.0, now));   // accepted
        ingestionService.ingest(new BidAskEvent("SOL-USD", 150.0, 151.0, late));  // dropped
        ingestionService.ingest(new BidAskEvent("SOL-USD", 150.0, 151.0, now));   // accepted

        Thread.sleep(300);
        // Queue was created (fresh events got through)
        assertThat(ingestionService.activeSymbols()).contains("SOL-USD");
    }
}
