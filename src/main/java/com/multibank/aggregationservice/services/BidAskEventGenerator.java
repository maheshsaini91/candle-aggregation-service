package com.multibank.aggregationservice.services;

import com.multibank.aggregationservice.models.BidAskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "candle.generator.enabled", havingValue = "true", matchIfMissing = true)
public class BidAskEventGenerator {

    private static final Logger log = LoggerFactory.getLogger(BidAskEventGenerator.class);
    private final IngestionService ingestionService;
    private final List<String> symbols;
    private final Random random = new Random();
    private final Map<String, Double> basePrices;

    // Track last price per symbol for random walk continuity
    private final ConcurrentHashMap<String, Double> lastPrices = new ConcurrentHashMap<>();

    public BidAskEventGenerator(
            IngestionService ingestionService,
            @Value("${candle.symbols:BTC-USD,ETH-USD,SOL-USD}") String symbolsConfig,
            @Value("${candle.base-prices:BTC-USD:65000,ETH-USD:3500,SOL-USD:150}") String basePricesConfig
    ) {
        this.ingestionService = ingestionService;
        this.symbols = Arrays.stream(symbolsConfig.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        this.basePrices = Arrays.stream(basePricesConfig.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.split(":")[0].trim(),
                        entry -> Double.parseDouble(entry.split(":")[1].trim())
                ));

        log.info("Event generator initialised for symbols: {}", this.symbols);
    }

    @Scheduled(fixedRateString = "${candle.generator.rate-ms:200}")
    public void generate() {
        long now = Instant.now().getEpochSecond();
        for (String symbol : symbols) {
            BidAskEvent event = generateEvent(symbol, now);
            ingestionService.ingest(event);
        }
    }

    private BidAskEvent generateEvent(String symbol, long timestamp) {
        double base    = basePrices.getOrDefault(symbol, 100.0);
        double lastMid = lastPrices.getOrDefault(symbol, base);

        // Random walk: ±0.05% per tick
        double change = lastMid * (random.nextDouble() * 0.001 - 0.0005);
        double mid    = Math.max(lastMid + change, 0.01); // floor at 0.01
        lastPrices.put(symbol, mid);

        // 0.1% spread
        double halfSpread = mid * 0.0005;
        return new BidAskEvent(symbol, mid - halfSpread, mid + halfSpread, timestamp);
    }
}
