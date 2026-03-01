package com.multibank.aggregationservice;

import com.multibank.aggregationservice.enums.Interval;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.repositories.CandleRepository;
import com.multibank.aggregationservice.repositories.InMemoryCandleRepository;
import com.multibank.aggregationservice.services.CandleWindowManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandleWindowManagerTest {

    private CandleRepository repository;
    private CandleWindowManager windowManager;

    @BeforeEach
    void setUp() {
        repository    = new InMemoryCandleRepository(new SimpleMeterRegistry());
        windowManager = new CandleWindowManager(repository);
    }

    @Test @DisplayName("1m bucket aligns to wall clock minute boundary")
    void bucket_1m() {
        assertThat(windowManager.resolveBucket(1620000075L, Interval.ONE_MIN))
                .isEqualTo(1620000060L);
    }

    @Test @DisplayName("1s bucket is exact second")
    void bucket_1s() {
        assertThat(windowManager.resolveBucket(1620000075L, Interval.ONE_SEC))
                .isEqualTo(1620000075L);
    }

    @Test @DisplayName("1h bucket aligns to hour boundary")
    void bucket_1h() {
        assertThat(windowManager.resolveBucket(1620003600L + 1234L, Interval.ONE_HOUR))
                .isEqualTo(1620003600L);
    }

    @Test @DisplayName("5s bucket: ts=7 → bucket=5")
    void bucket_5s() {
        assertThat(windowManager.resolveBucket(1620000007L, Interval.FIVE_SEC))
                .isEqualTo(1620000005L);
    }

    @Test @DisplayName("Events at :00 and :59 land in same 1m bucket")
    void sameWindowSameBucket() {
        assertThat(windowManager.resolveBucket(1620000001L, Interval.ONE_MIN))
                .isEqualTo(windowManager.resolveBucket(1620000059L, Interval.ONE_MIN));
    }

    @Test @DisplayName("Events at :59 and :60 land in different buckets exactly 60s apart")
    void adjacentWindowsDifferentBuckets() {
        long b1 = windowManager.resolveBucket(1620000059L, Interval.ONE_MIN);
        long b2 = windowManager.resolveBucket(1620000060L, Interval.ONE_MIN);
        assertThat(b2 - b1).isEqualTo(60L);
    }

    @Test @DisplayName("No finalization on first event for a (symbol, interval)")
    void noFinalizationOnFirstEvent() {
        CandleKey key = new CandleKey("BTC-USD", 60L, 1620000000L);
        repository.updateActiveCandle(key, 29505.0);
        windowManager.checkAndFinalizeIfWindowClosed("BTC-USD", Interval.ONE_MIN, 1620000000L);
        assertThat(repository.finalizedCandleCount()).isEqualTo(0);
    }

    @Test @DisplayName("Finalization triggered when bucket rolls over")
    void finalizationOnRollover() {
        String symbol = "BTC-USD";
        CandleKey key = new CandleKey(symbol, 60L, 1620000000L);
        repository.updateActiveCandle(key, 29505.0);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000000L);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000060L);
        assertThat(repository.finalizedCandleCount()).isEqualTo(1);
    }

    @Test @DisplayName("Same bucket repeated — no duplicate finalization")
    void sameBucketNoDuplicate() {
        String symbol = "BTC-USD";
        CandleKey key = new CandleKey(symbol, 60L, 1620000000L);
        repository.updateActiveCandle(key, 29505.0);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000000L);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000000L);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000000L);
        assertThat(repository.finalizedCandleCount()).isEqualTo(0);
    }

    @Test @DisplayName("Skipped buckets are all finalized when events jump forward")
    void skippedBucketsFinalized() {
        String symbol = "BTC-USD";
        long intervalSec = 60L;
        repository.updateActiveCandle(new CandleKey(symbol, intervalSec, 1620000000L), 29505.0);
        repository.updateActiveCandle(new CandleKey(symbol, intervalSec, 1620000060L), 29510.0);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000000L);
        windowManager.checkAndFinalizeIfWindowClosed(symbol, Interval.ONE_MIN, 1620000120L);
        assertThat(repository.finalizedCandleCount()).isEqualTo(2);
        assertThat(repository.findFinalized(symbol, intervalSec, 1620000000L, 1620000120L))
                .hasSize(2)
                .extracting(c -> c.time())
                .containsExactly(1620000000L, 1620000060L);
    }
}
