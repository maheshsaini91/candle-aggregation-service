package com.multibank.aggregationservice;

import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.repositories.CandleRepository;
import com.multibank.aggregationservice.repositories.InMemoryCandleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandleRepositoryTest {

    private CandleRepository repository;
    private static final String SYMBOL   = "BTC-USD";
    private static final long INTERVAL   = 60L;
    private static final long BUCKET     = 1620000000L;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCandleRepository(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("First event creates a candle with all OHLC = first price")
    void firstEventCreatesCandle() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL, BUCKET);
        repository.updateActiveCandle(key, 29505.0);

        Candle s = repository.getActiveSnapshot(key);
        assertThat(s).isNotNull();
        assertThat(s.open()).isEqualTo(29505.0);
        assertThat(s.high()).isEqualTo(29505.0);
        assertThat(s.low()).isEqualTo(29505.0);
        assertThat(s.close()).isEqualTo(29505.0);
        assertThat(s.volume()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Subsequent events update high, low, close and increment volume")
    void subsequentEventsUpdateOHLC() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL, BUCKET);
        repository.updateActiveCandle(key, 29505.0);
        repository.updateActiveCandle(key, 29485.0);
        repository.updateActiveCandle(key, 29525.0);

        Candle s = repository.getActiveSnapshot(key);
        assertThat(s.open()).isEqualTo(29505.0);
        assertThat(s.high()).isEqualTo(29525.0);
        assertThat(s.low()).isEqualTo(29485.0);
        assertThat(s.close()).isEqualTo(29525.0);
        assertThat(s.volume()).isEqualTo(3L);
    }

    @Test
    @DisplayName("High only moves upward")
    void highOnlyMovesUpward() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL, BUCKET);
        repository.updateActiveCandle(key, 100.0);
        repository.updateActiveCandle(key, 90.0);
        repository.updateActiveCandle(key, 95.0);
        assertThat(repository.getActiveSnapshot(key).high()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Low only moves downward")
    void lowOnlyMovesDownward() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL, BUCKET);
        repository.updateActiveCandle(key, 100.0);
        repository.updateActiveCandle(key, 110.0);
        repository.updateActiveCandle(key, 105.0);
        assertThat(repository.getActiveSnapshot(key).low()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Finalizing moves candle from active to finalized map")
    void finalizationMovesCandle() {
        CandleKey key = new CandleKey(SYMBOL, INTERVAL, BUCKET);
        repository.updateActiveCandle(key, 29505.0);
        repository.finalizeCandle(key);

        assertThat(repository.activeCandleCount()).isEqualTo(0);
        assertThat(repository.finalizedCandleCount()).isEqualTo(1);
        assertThat(repository.getActiveSnapshot(key)).isNull();
    }

    @Test
    @DisplayName("Finalizing non-existent key is a no-op")
    void finalizingNonExistentKeyIsNoOp() {
        repository.finalizeCandle(new CandleKey(SYMBOL, INTERVAL, BUCKET));
        assertThat(repository.finalizedCandleCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("findFinalized returns only candles in the requested range")
    void findFinalizedReturnsInRange() {
        for (int i = 0; i < 4; i++) {
            CandleKey key = new CandleKey(SYMBOL, INTERVAL, BUCKET + (i * 60L));
            repository.updateActiveCandle(key, 100.0 + i);
            repository.finalizeCandle(key);
        }
        List<Candle> result = repository.findFinalized(SYMBOL, INTERVAL, BUCKET + 60, BUCKET + 120);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).time()).isEqualTo(BUCKET + 60);
        assertThat(result.get(1).time()).isEqualTo(BUCKET + 120);
    }

    @Test
    @DisplayName("findFinalized does not return candles for a different symbol")
    void findFinalizedIsolatedBySymbol() {
        CandleKey key = new CandleKey("BTC-USD", INTERVAL, BUCKET);
        repository.updateActiveCandle(key, 29505.0);
        repository.finalizeCandle(key);
        assertThat(repository.findFinalized("ETH-USD", INTERVAL, BUCKET, BUCKET + 60)).isEmpty();
    }

    @Test
    @DisplayName("flushAllActive finalizes all open candles")
    void flushAllActiveFinalizes() {
        for (int i = 0; i < 3; i++) {
            repository.updateActiveCandle(new CandleKey("SYM" + i, INTERVAL, BUCKET), 100.0);
        }
        repository.flushAllActive();
        assertThat(repository.activeCandleCount()).isEqualTo(0);
        assertThat(repository.finalizedCandleCount()).isEqualTo(3);
    }
}
