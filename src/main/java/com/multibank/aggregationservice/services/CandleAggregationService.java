package com.multibank.aggregationservice.services;

import com.multibank.aggregationservice.dtos.HistoryResponse;
import com.multibank.aggregationservice.enums.Interval;
import com.multibank.aggregationservice.models.BidAskEvent;
import com.multibank.aggregationservice.models.Candle;
import com.multibank.aggregationservice.models.CandleKey;
import com.multibank.aggregationservice.repositories.CandleRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CandleAggregationService {
    private static final Logger log = LoggerFactory.getLogger(CandleAggregationService.class);

    private final CandleRepository repository;
    private final CandleWindowManager windowManager;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Timer historyResponseTimer;

    public CandleAggregationService(
            CandleRepository repository,
            CandleWindowManager windowManager,
            MeterRegistry meterRegistry
    ) {
        this.repository    = repository;
        this.windowManager = windowManager;
        this.meterRegistry = meterRegistry;

        this.historyResponseTimer = Timer.builder("candle.history.response_time")
                .description("Latency of GET /history responses")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // Gauge: total active (open) candle windows across all symbols/intervals
        Gauge.builder("candle.active.count", repository, CandleRepository::activeCandleCount)
                .description("Number of currently open candle windows")
                .register(meterRegistry);
    }

    public void processInternal(BidAskEvent event) {
        log.debug("Processing event: symbol={} bid={} ask={} mid={} ts={}",
                event.symbol(), event.bid(), event.ask(),
                String.format("%.2f", event.midPrice()), event.timestamp());

        double midPrice = event.midPrice();

        for (Interval interval : Interval.values()) {
            long bucket = windowManager.resolveBucket(event.timestamp(), interval);
            windowManager.checkAndFinalizeIfWindowClosed(event.symbol(), interval, bucket);

            CandleKey key = new CandleKey(event.symbol(), interval.getSeconds(), bucket);
            repository.updateActiveCandle(key, midPrice);
        }
    }

    public HistoryResponse getHistory(String symbol, String intervalLabel, long from, long to) {
        return historyResponseTimer.record(() -> {
            Interval interval = Interval.fromLabel(intervalLabel)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported interval: " + intervalLabel +
                                    ". Supported: " + supportedIntervalLabels()));

            List<Candle> candles = repository.findFinalized( symbol, interval.getSeconds(), from, to);

            if (candles.isEmpty()) {
                log.debug("No data: symbol={} interval={} from={} to={}", symbol, intervalLabel, from, to);
                return HistoryResponse.noData();
            }

            List<Long>   t = new ArrayList<>();
            List<Double> o = new ArrayList<>();
            List<Double> h = new ArrayList<>();
            List<Double> l = new ArrayList<>();
            List<Double> c = new ArrayList<>();
            List<Long>   v = new ArrayList<>();

            for (Candle candle : candles) {
                t.add(candle.time());
                o.add(candle.open());
                h.add(candle.high());
                l.add(candle.low());
                c.add(candle.close());
                v.add(candle.volume());
            }

            log.debug("Returning {} candles: symbol={} interval={}", candles.size(), symbol, intervalLabel);
            return HistoryResponse.ok(t, o, h, l, c, v);
        });
    }

    @PreDestroy
    public void onShutdown() {
        log.info("CandleAggregationService shutting down — flushing active candles...");
        repository.flushAllActive();
        log.info("Shutdown flush complete. Total finalized: {}", repository.finalizedCandleCount());
    }

    private String supportedIntervalLabels() {
        StringBuilder sb = new StringBuilder();
        for (Interval i : Interval.values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(i.getLabel());
        }
        return sb.toString();
    }
}
