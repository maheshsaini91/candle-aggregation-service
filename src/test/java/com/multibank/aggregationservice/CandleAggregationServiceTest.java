package com.multibank.aggregationservice;

import com.multibank.aggregationservice.dtos.HistoryResponse;
import com.multibank.aggregationservice.enums.Interval;
import com.multibank.aggregationservice.models.BidAskEvent;
import com.multibank.aggregationservice.repositories.CandleRepository;
import com.multibank.aggregationservice.services.CandleAggregationService;
import com.multibank.aggregationservice.services.CandleWindowManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleAggregationServiceTest {

    private CandleRepository repository;
    private CandleWindowManager windowManager;
    private CandleAggregationService service;

    private static final long BASE_TS = 1620000000L;
    private static final String SYMBOL = "BTC-USD";

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        repository    = new CandleRepository(metrics);
        windowManager = new CandleWindowManager(repository);
        service       = new CandleAggregationService(repository, windowManager, metrics);
    }

    @Test
    @DisplayName("Mid-price (bid+ask)/2 is used for OHLC — not bid or ask alone")
    void midPriceUsedForOHLC() {
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS));
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 60));

        HistoryResponse r = service.getHistory(SYMBOL, "1m", BASE_TS, BASE_TS + 59);
        assertThat(r.s()).isEqualTo("ok");
        assertThat(r.o()).containsExactly(29505.0);
        assertThat(r.h()).containsExactly(29505.0);
        assertThat(r.l()).containsExactly(29505.0);
        assertThat(r.c()).containsExactly(29505.0);
    }

    @Test
    @DisplayName("OHLC correctly computed: first=open, max=high, min=low, last=close")
    void ohlcCorrectlyComputed() {
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS));      // mid=29505
        service.processInternal(new BidAskEvent(SYMBOL, 29480.0, 29490.0, BASE_TS + 10)); // mid=29485
        service.processInternal(new BidAskEvent(SYMBOL, 29520.0, 29530.0, BASE_TS + 50)); // mid=29525
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 60));

        HistoryResponse r = service.getHistory(SYMBOL, "1m", BASE_TS, BASE_TS + 59);
        assertThat(r.o()).containsExactly(29505.0);
        assertThat(r.h()).containsExactly(29525.0);
        assertThat(r.l()).containsExactly(29485.0);
        assertThat(r.c()).containsExactly(29525.0);
    }

    @Test
    @DisplayName("Volume equals number of ticks (events) in the window")
    void volumeIsTickCount() {
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS));
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 10));
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 20));
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS + 60));

        HistoryResponse r = service.getHistory(SYMBOL, "1m", BASE_TS, BASE_TS + 59);
        assertThat(r.v()).containsExactly(3L);
    }

    @Test
    @DisplayName("Separate windows produce separate candles with correct timestamps")
    void separateWindowsProduceSeparateCandles() {
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS));
        service.processInternal(new BidAskEvent(SYMBOL, 30000.0, 30010.0, BASE_TS + 60));
        service.processInternal(new BidAskEvent(SYMBOL, 30000.0, 30010.0, BASE_TS + 120));

        HistoryResponse r = service.getHistory(SYMBOL, "1m", BASE_TS, BASE_TS + 119);
        assertThat(r.t()).hasSize(2);
        assertThat(r.t().get(0)).isEqualTo(BASE_TS);
        assertThat(r.t().get(1)).isEqualTo(BASE_TS + 60);
    }

    @Test
    @DisplayName("Bucket timestamps are wall-clock aligned, not event-relative")
    void bucketsAreWallClockAligned() {
        long eventTs       = 1620000075L; // 75s into the minute
        long expectedBucket = 1620000060L; // aligned to :00

        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, eventTs));
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, eventTs + 60));

        HistoryResponse r = service.getHistory(SYMBOL, "1m", expectedBucket, expectedBucket + 59);
        assertThat(r.t()).containsExactly(expectedBucket);
    }

    @Test
    @DisplayName("Returns no_data for a range with no finalized candles")
    void returnsNoDataForEmptyRange() {
        HistoryResponse r = service.getHistory(SYMBOL, "1m", BASE_TS, BASE_TS + 60);
        assertThat(r.s()).isEqualTo("no_data");
        assertThat(r.t()).isEmpty();
    }

    @Test
    @DisplayName("Multiple symbols are aggregated independently without interference")
    void multipleSymbolsAreIndependent() {
        service.processInternal(new BidAskEvent("BTC-USD", 29500.0, 29510.0, BASE_TS));
        service.processInternal(new BidAskEvent("ETH-USD", 3500.0,  3510.0,  BASE_TS));
        service.processInternal(new BidAskEvent("BTC-USD", 29500.0, 29510.0, BASE_TS + 60));
        service.processInternal(new BidAskEvent("ETH-USD", 3500.0,  3510.0,  BASE_TS + 60));

        assertThat(service.getHistory("BTC-USD", "1m", BASE_TS, BASE_TS + 59).o())
                .containsExactly(29505.0);
        assertThat(service.getHistory("ETH-USD", "1m", BASE_TS, BASE_TS + 59).o())
                .containsExactly(3505.0);
    }

    @Test
    @DisplayName("All intervals are active simultaneously after first event")
    void allIntervalsActiveSimultaneously() {
        service.processInternal(new BidAskEvent(SYMBOL, 29500.0, 29510.0, BASE_TS));
        assertThat(repository.activeCandleCount()).isEqualTo(Interval.values().length);
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for unsupported interval label")
    void throwsForUnsupportedInterval() {
        assertThatThrownBy(() -> service.getHistory(SYMBOL, "2m", BASE_TS, BASE_TS + 120))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported interval");
    }
}
